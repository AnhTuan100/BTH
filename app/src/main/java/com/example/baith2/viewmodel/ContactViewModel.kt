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
        ContactDatabase.getDatabase(application).contactDao()
    )

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    val contactsLiveData: LiveData<List<Contact>> = _contacts.asLiveData()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
}
