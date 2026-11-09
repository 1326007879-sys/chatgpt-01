package io.github.chatgpt01.plugins.notes

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class Note(@com.google.gson.annotations.SerializedName("id") val id: String, @com.google.gson.annotations.SerializedName("text") val text: String)
/** One worker serializes requests. Private data is committed atomically and never silently discarded. */
class NoteStore(private val file: File) {
    companion object { private val locks = java.util.concurrent.ConcurrentHashMap<String,Any>() }
    private val lock = locks.computeIfAbsent(file.canonicalPath) { Any() }
    fun read(): List<Note> = synchronized(lock) {
        if (!file.exists()) return@synchronized emptyList()
        require(file.length() <= 1048576)
        Gson().fromJson(file.readText(Charsets.UTF_8),Array<Note>::class.java).toList().also { notes ->
            require(notes.size <= 200 && notes.map { it.id }.distinct().size == notes.size)
            require(notes.all { it.text.length in 1..1000 && it.id.length in 1..80 })
        }
    }
    private fun write(notes: List<Note>) {
        file.parentFile!!.mkdirs()
        val temp = File(file.parentFile,"notes-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { it.write(Gson().toJson(notes).toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    fun add(text: String): Note = synchronized(lock) {
        require(text.isNotBlank() && text.length <= 1000)
        val notes = read(); require(notes.size < 200)
        Note(UUID.randomUUID().toString(),text).also { write(notes + it) }
    }
    fun search(query: String, page: Int = 1): List<Note> = synchronized(lock) {
        require(page in 1..20)
        read().filter { it.text.contains(query,ignoreCase=true) }.drop((page-1)*10).take(10)
    }
    fun delete(id: String): Boolean = synchronized(lock) {
        val notes = read(); val next = notes.filterNot { it.id == id }
        if (next.size == notes.size) return@synchronized false
        write(next); true
    }
}
