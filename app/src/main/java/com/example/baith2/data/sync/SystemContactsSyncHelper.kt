package com.example.baith2.data.sync

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.RawContacts
import com.example.baith2.data.entity.ContactEntity
import com.example.baith2.data.entity.PhoneNumberEntity

/**
 * Helper đọc / ghi Android Contacts Provider (Content Provider hệ thống).
 * Đây là tầng giao tiếp duy nhất với ContactsContract — tất cả logic
 * nghiệp vụ sync nằm ở [ContactRepository].
 */
class SystemContactsSyncHelper(private val context: Context) {

    // ── Data class đại diện một liên hệ hệ thống ─────────────────────────────

    data class SystemContact(
        val systemId: Long,          // ContactsContract.Contacts._ID
        val rawContactId: Long,      // RawContacts._ID (cần khi ghi)
        val name: String,
        val phones: List<String>,
        val email: String
    )

    // ── ĐỌC từ hệ thống ──────────────────────────────────────────────────────

    /**
     * Trả về toàn bộ danh bạ từ Contacts Provider của hệ thống.
     * Yêu cầu quyền READ_CONTACTS đã được cấp.
     */
    fun readAllSystemContacts(): List<SystemContact> {
        val contacts = mutableMapOf<Long, SystemContact>()

        // 1. Đọc danh sách tên
        val nameCursor: Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )
        nameCursor?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = cur.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cur.moveToNext()) {
                val id = cur.getLong(idCol)
                val name = cur.getString(nameCol) ?: ""
                contacts[id] = SystemContact(id, -1L, name, emptyList(), "")
            }
        }

        if (contacts.isEmpty()) return emptyList()

        // 2. Đọc rawContactId
        val rawCursor: Cursor? = context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.CONTACT_ID),
            null, null, null
        )
        val rawMap = mutableMapOf<Long, Long>() // contactId -> rawContactId
        rawCursor?.use { cur ->
            val rawIdCol = cur.getColumnIndexOrThrow(RawContacts._ID)
            val contactIdCol = cur.getColumnIndexOrThrow(RawContacts.CONTACT_ID)
            while (cur.moveToNext()) {
                val rawId = cur.getLong(rawIdCol)
                val contactId = cur.getLong(contactIdCol)
                rawMap[contactId] = rawId // lấy rawId cuối cùng (đủ cho mục đích này)
            }
        }

        // 3. Đọc số điện thoại
        val phoneMap = mutableMapOf<Long, MutableList<String>>() // contactId -> phones
        val phoneCursor: Cursor? = context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER),
            null, null, null
        )
        phoneCursor?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val numCol = cur.getColumnIndexOrThrow(Phone.NUMBER)
            while (cur.moveToNext()) {
                val id = cur.getLong(idCol)
                val num = cur.getString(numCol) ?: continue
                phoneMap.getOrPut(id) { mutableListOf() }.add(num)
            }
        }

        // 4. Đọc email
        val emailMap = mutableMapOf<Long, String>()
        val emailCursor: Cursor? = context.contentResolver.query(
            Email.CONTENT_URI,
            arrayOf(Email.CONTACT_ID, Email.ADDRESS),
            null, null, null
        )
        emailCursor?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(Email.CONTACT_ID)
            val addrCol = cur.getColumnIndexOrThrow(Email.ADDRESS)
            while (cur.moveToNext()) {
                val id = cur.getLong(idCol)
                val addr = cur.getString(addrCol) ?: continue
                if (!emailMap.containsKey(id)) emailMap[id] = addr
            }
        }

        // 5. Gộp kết quả
        return contacts.values.map { sc ->
            sc.copy(
                rawContactId = rawMap[sc.systemId] ?: -1L,
                phones = phoneMap[sc.systemId] ?: emptyList(),
                email = emailMap[sc.systemId] ?: ""
            )
        }
    }

    // ── GHI vào hệ thống ─────────────────────────────────────────────────────

    /**
     * Tạo một liên hệ mới trong Danh bạ hệ thống.
     * Trả về [ContactsContract.Contacts._ID] của liên hệ vừa tạo, hoặc -1 nếu thất bại.
     * Yêu cầu quyền WRITE_CONTACTS đã được cấp.
     */
    fun insertSystemContact(
        name: String,
        phones: List<String>,
        email: String
    ): Long {
        val ops = arrayListOf<ContentProviderOperation>()

        // Bước 0: tạo RawContact rỗng (account = null → local account)
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build()

        // Bước 1: tên
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, name)
            .build()

        // Bước 2: số điện thoại
        phones.filter { it.isNotBlank() }.forEach { num ->
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, num)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }

        // Bước 3: email
        if (email.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, email)
                .withValue(Email.TYPE, Email.TYPE_HOME)
                .build()
        }

        return try {
            val results = context.contentResolver.applyBatch(
                ContactsContract.AUTHORITY, ops
            )
            // Lấy rawContactId từ kết quả batch, sau đó tra cứu contactId
            val rawContactUri = results[0].uri ?: return -1L
            val rawContactId = ContentUris.parseId(rawContactUri)
            queryContactIdFromRaw(rawContactId)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * Cập nhật liên hệ đã có trong hệ thống (xác định bởi [rawContactId]).
     */
    fun updateSystemContact(
        rawContactId: Long,
        name: String,
        phones: List<String>,
        email: String
    ) {
        if (rawContactId == -1L) return
        val ops = arrayListOf<ContentProviderOperation>()
        val where = "${ContactsContract.Data.RAW_CONTACT_ID} = ?"
        val whereArgs = arrayOf(rawContactId.toString())

        // Xóa dữ liệu cũ (tên, phone, email) thuộc raw contact này
        ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "$where AND ${ContactsContract.Data.MIMETYPE} IN (?,?,?)",
                arrayOf(
                    rawContactId.toString(),
                    StructuredName.CONTENT_ITEM_TYPE,
                    Phone.CONTENT_ITEM_TYPE,
                    Email.CONTENT_ITEM_TYPE
                )
            ).build()

        // Ghi lại tên
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, name)
            .build()

        // Ghi lại số điện thoại
        phones.filter { it.isNotBlank() }.forEach { num ->
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, num)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }

        // Ghi lại email
        if (email.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, email)
                .withValue(Email.TYPE, Email.TYPE_HOME)
                .build()
        }

        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Xóa liên hệ khỏi hệ thống theo [systemContactId].
     */
    fun deleteSystemContact(systemContactId: Long) {
        val uri = ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI, systemContactId
        )
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun queryContactIdFromRaw(rawContactId: Long): Long {
        val cursor = context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        } ?: -1L
    }

    /** Lấy rawContactId từ systemContactId (ContactsContract.Contacts._ID) */
    fun getRawContactId(systemContactId: Long): Long {
        val cursor = context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ?",
            arrayOf(systemContactId.toString()),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        } ?: -1L
    }
}
