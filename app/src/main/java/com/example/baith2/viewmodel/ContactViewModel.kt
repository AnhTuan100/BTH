package com.example.baith2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.baith2.data.database.ContactDatabase
import com.example.baith2.data.entity.MessageEntity
import com.example.baith2.data.repository.ContactRepository
import com.example.baith2.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContactRepository = ContactRepository(
        dao = ContactDatabase.getDatabase(application).contactDao(),
        context = application          // truyền context để sync helper dùng ContentResolver
    )

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    val contactsLiveData: LiveData<List<Contact>> = _contacts.asLiveData()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── Trạng thái đồng bộ ────────────────────────────────────────────────────
    enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }

    data class SyncResult(
        val state: SyncState = SyncState.IDLE,
        val imported: Int = 0,
        val exported: Int = 0,
        val errorMessage: String = ""
    )

    private val _syncResult = MutableStateFlow(SyncResult())
    val syncResult: StateFlow<SyncResult> = _syncResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allContactsWithPhones.collect { listWithPhones ->
                _contacts.value = listWithPhones.map { cwp ->
                    Contact(
                        id = cwp.contact.id,
                        name = cwp.contact.name,
                        email = cwp.contact.email,
                        phones = cwp.phoneNumbers.map { it.number }
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun filteredContacts(): List<Contact> {
        val query = _searchQuery.value
        if (query.isBlank()) return _contacts.value
        return _contacts.value.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.phones.any { phone -> phone.contains(query) }
        }
    }

    fun getContactById(id: Long): Contact? = _contacts.value.find { it.id == id }

    fun addContact(name: String, email: String, phones: List<String>) {
        viewModelScope.launch {
            repository.addContact(name.trim(), email.trim(), phones)
        }
    }

    fun updateContact(id: Long, name: String, email: String, phones: List<String>) {
        viewModelScope.launch {
            repository.updateContact(id, name.trim(), email.trim(), phones)
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact.id)
        }
    }

    fun getMessagesForContact(contactId: Long): Flow<List<MessageEntity>> =
        repository.getMessagesByContactId(contactId)

    fun saveMessage(contactId: Long, phoneNumber: String, content: String) {
        viewModelScope.launch {
            repository.saveMessage(contactId, phoneNumber, content)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    // ── Đồng bộ với Danh bạ hệ thống ─────────────────────────────────────────

    /**
     * Chạy đồng bộ 2 chiều trong coroutine scope của ViewModel.
     * Cập nhật [syncResult] trong suốt quá trình để UI phản ánh.
     */
    fun syncWithSystemContacts() {
        if (_syncResult.value.state == SyncState.SYNCING) return   // tránh chạy đồng thời
        viewModelScope.launch {
            _syncResult.value = SyncResult(state = SyncState.SYNCING)
            try {
                val (imported, exported) = repository.syncBidirectional()
                _syncResult.value = SyncResult(
                    state = SyncState.SUCCESS,
                    imported = imported,
                    exported = exported
                )
            } catch (e: Exception) {
                _syncResult.value = SyncResult(
                    state = SyncState.ERROR,
                    errorMessage = e.message ?: "Lỗi không xác định"
                )
            }
        }
    }

    /** Reset kết quả sync về IDLE (sau khi UI đã hiển thị thông báo) */
    fun resetSyncResult() {
        _syncResult.value = SyncResult()
    }
}

