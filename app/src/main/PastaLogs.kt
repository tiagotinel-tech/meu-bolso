package dev.tiagotinel.meubolso

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** Pasta escolhida pelo usuário (ex.: Documentos/MeuBolso) onde os .txt são gravados. */
object LogFolder {
    private const val KEY = "log_tree_uri"
    private val cache = HashMap<String, Uri>()

    private fun prefs(c: Context) = c.getSharedPreferences("meubolso", Context.MODE_PRIVATE)

    fun get(c: Context): Uri? = prefs(c).getString(KEY, null)?.let(Uri::parse)

    fun set(c: Context, uri: Uri) {
        c.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs(c).edit().putString(KEY, uri.toString()).apply()
        synchronized(cache) { cache.clear() }
    }

    fun isReady(c: Context): Boolean {
        val uri = get(c) ?: return false
        return c.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }

    /** Acrescenta texto em <pasta>/<sub>/<name>.txt, criando o que faltar. */
    fun append(c: Context, sub: String, name: String, text: String): Boolean {
        if (!isReady(c)) return false
        return try {
            write(c, sub, name, text)
        } catch (_: Exception) {
            synchronized(cache) { cache.clear() } // arquivo apagado ou movido: tenta de novo do zero
            try { write(c, sub, name, text) } catch (_: Exception) { false }
        }
    }

    private fun write(c: Context, sub: String, name: String, text: String): Boolean {
        val tree = get(c) ?: return false
        val cr = c.contentResolver
        val dir = synchronized(cache) { cache[sub] } ?: run {
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            (child(c, tree, rootId, sub)
                ?: DocumentsContract.createDocument(cr, root, Document.MIME_TYPE_DIR, sub))
                ?.also { synchronized(cache) { cache[sub] = it } }
        } ?: return false

        val fileName = "$name.txt"
        val key = "$sub/$fileName"
        val file = synchronized(cache) { cache[key] } ?: run {
            (child(c, tree, DocumentsContract.getDocumentId(dir), fileName)
                ?: DocumentsContract.createDocument(cr, dir, "text/plain", fileName))
                ?.also { synchronized(cache) { cache[key] = it } }
        } ?: return false

        cr.openOutputStream(file, "wa")?.use { it.write(text.toByteArray()) } ?: return false
        return true
    }

    private fun child(c: Context, tree: Uri, parentId: String, name: String): Uri? {
        val list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        c.contentResolver.query(list, cols, null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                if (cur.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0))
            }
        }
        return null
    }
}
