package com.example.baith2.data.repository

import android.content.Context
import com.example.baith2.data.dao.ContactDao
import com.example.baith2.data.entity.ContactEntity
import com.example.baith2.data.entity.MessageEntity
import com.example.baith2.data.entity.PhoneNumberEntity
import com.example.baith2.data.relation.ContactWithPhones
import com.example.baith2.data.sync.SystemContactsSyncHelper
import kotlinx.coroutines.flow.Flow

class ContactRepository(
    private val dao: ContactDao,
    private val context: Context
) {

    val allContactsWithPhones: Flow<List<ContactWithPhones>> =
        dao.getAllContactsWithPhones()

    private val syncHelper = SystemContactsSyncHelper(context)

    suspend fun getContactWithPhones(id: Long): ContactWithPhones? =
        dao.getContactWithPhones(id)

    suspend fun addContact(name: String, email: String, phones: List<String>) {
        val contactId = dao.insertContact(ContactEntity(name = name, email = email))
        phones.filter { it.isNotBlank() }.forEach { number ->
            dao.insertPhoneNumber(PhoneNumberEntity(contactId = contactId, number = number))
        }
    }

    suspend fun updateContact(id: Long, name: String, email: String, phones: List<String>) {
        // Lấy systemContactId hiện có (nếu đã sync)
        val existing = dao.getContactWithPhones(id)
        val sysId = existing?.contact?.systemContactId

        dao.updateContact(ContactEntity(id = id, name = name, email = email, systemContactId = sysId))
        dao.deletePhonesByContactId(id)
        phones.filter { it.isNotBlank() }.forEach { number ->
            dao.insertPhoneNumber(PhoneNumberEntity(contactId = id, number = number))
        }

        // Nếu liên hệ đã được đồng bộ → cập nhật luôn bên hệ thống
        if (sysId != null && sysId != -1L) {
            val rawId = syncHelper.getRawContactId(sysId)
            syncHelper.updateSystemContact(rawId, name, phones, email)
        }
    }

    suspend fun deleteContact(contactId: Long) {
        // Xóa bên hệ thống nếu đã đồng bộ
        val existing = dao.getContactWithPhones(contactId)
        val sysId = existing?.contact?.systemContactId
        if (sysId != null && sysId != -1L) {
            syncHelper.deleteSystemContact(sysId)
        }
        dao.deleteContactById(contactId)
    }

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

    // ── Đồng bộ 2 chiều ───────────────────────────────────────────────────────

    /**
     * Đồng bộ 2 chiều giữa Danh bạ hệ thống và Room Database.
     *
     * Chiều 1 — HỆ THỐNG → ỨNG DỤNG:
     *   Mỗi liên hệ hệ thống được so khớp với [ContactEntity.systemContactId].
     *   - Nếu đã có trong DB → cập nhật tên / số / email.
     *   - Nếu chưa có → thêm mới và ghi lại systemContactId.
     *
     * Chiều 2 — ỨNG DỤNG → HỆ THỐNG:
     *   Mỗi liên hệ trong DB chưa có [systemContactId] → tạo mới bên hệ thống
     *   và cập nhật [systemContactId] vào DB.
     *
     * @return Cặp (importedCount, exportedCount)
     */
    suspend fun syncBidirectional(): Pair<Int, Int> {
        val systemContacts = syncHelper.readAllSystemContacts()
        val localSnapshot = dao.getAllContactsSnapshot()

        var imported = 0
        var exported = 0

        // ── Chiều 1: Hệ thống → Ứng dụng ────────────────────────────────────
        for (sys in systemContacts) {
            val existing = dao.getContactBySystemId(sys.systemId)
            if (existing != null) {
                // Đã có → cập nhật nếu dữ liệu thay đổi
                val localContact = existing.contact
                if (localContact.name != sys.name || localContact.email != sys.email ||
                    existing.phoneNumbers.map { it.number }.toSet() != sys.phones.toSet()
                ) {
                    dao.upsertContact(
                        localContact.copy(
                            name = sys.name,
                            email = sys.email,
                            systemContactId = sys.systemId
                        )
                    )
                    dao.deletePhonesByContactId(localContact.id)
                    dao.upsertPhoneNumbers(
                        sys.phones.map { PhoneNumberEntity(contactId = localContact.id, number = it) }
                    )
                    imported++
                }
            } else {
                // Chưa có → thêm mới vào DB
                val newId = dao.upsertContact(
                    ContactEntity(
                        name = sys.name,
                        email = sys.email,
                        systemContactId = sys.systemId
                    )
                )
                dao.upsertPhoneNumbers(
                    sys.phones.map { PhoneNumberEntity(contactId = newId, number = it) }
                )
                imported++
            }
        }

        // ── Chiều 2: Ứng dụng → Hệ thống ────────────────────────────────────
        for (local in localSnapshot) {
            if (local.contact.systemContactId == null) {
                // Chưa từng sync → tạo mới bên hệ thống
                val sysId = syncHelper.insertSystemContact(
                    name = local.contact.name,
                    phones = local.phoneNumbers.map { it.number },
                    email = local.contact.email
                )
                if (sysId != -1L) {
                    dao.upsertContact(local.contact.copy(systemContactId = sysId))
                    exported++
                }
            }
        }

        return Pair(imported, exported)
    }
}

