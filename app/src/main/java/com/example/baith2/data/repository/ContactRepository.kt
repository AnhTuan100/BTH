package com.example.baith2.data.repository

import com.example.baith2.data.dao.ContactDao
import com.example.baith2.data.entity.ContactEntity
import com.example.baith2.data.entity.MessageEntity
import com.example.baith2.data.entity.PhoneNumberEntity
import com.example.baith2.data.relation.ContactWithPhones
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val dao: ContactDao) {

    val allContactsWithPhones: Flow<List<ContactWithPhones>> =
        dao.getAllContactsWithPhones()

    suspend fun getContactWithPhones(id: Long): ContactWithPhones? =
        dao.getContactWithPhones(id)

    suspend fun addContact(name: String, email: String, phones: List<String>) {
        val contactId = dao.insertContact(ContactEntity(name = name, email = email))
        phones.filter { it.isNotBlank() }.forEach { number ->
            dao.insertPhoneNumber(PhoneNumberEntity(contactId = contactId, number = number))
        }
    }

    suspend fun updateContact(id: Long, name: String, email: String, phones: List<String>) {
        dao.updateContact(ContactEntity(id = id, name = name, email = email))
        dao.deletePhonesByContactId(id)
        phones.filter { it.isNotBlank() }.forEach { number ->
            dao.insertPhoneNumber(PhoneNumberEntity(contactId = id, number = number))
        }
    }

    suspend fun deleteContact(contactId: Long) =
        dao.deleteContactById(contactId)

    fun getMessagesByContactId(contactId: Long): Flow<List<MessageEntity>> =
        dao.getMessagesByContactId(contactId)

    suspend fun saveMessage(contactId: Long, phoneNumber: String, content: String): Long =
        dao.insertMessage(
            MessageEntity(
                contactId = contactId,
                phoneNumber = phoneNumber,
                content = content
            )
        )

    suspend fun deleteMessage(messageId: Long) =
        dao.deleteMessageById(messageId)
}
