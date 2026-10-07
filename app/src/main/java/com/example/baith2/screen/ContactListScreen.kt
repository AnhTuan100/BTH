package com.example.baith2.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.baith2.component.ContactItem
import com.example.baith2.viewmodel.ContactViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    viewModel: ContactViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    val context = LocalContext.current

    val searchQuery by viewModel.searchQuery.collectAsState()
    val contacts by viewModel.contactsLiveData.observeAsState(emptyList())

    val filteredContacts = remember(contacts, searchQuery) {
        viewModel.filteredContacts()
    }

    fun launchDialer(phone: String) {
        context.startActivity(
            Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Danh Bạ") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "Thêm liên lạc")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Tìm theo tên hoặc số điện thoại…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredContacts, key = { it.id }) { contact ->
                    ContactItem(
                        contact = contact,
                        onItemClick = { onNavigateToDetail(contact.id) },
                        onEditClick = { onNavigateToEdit(contact.id) },
                        onDeleteClick = { viewModel.deleteContact(contact) },
                        onCallClick = { launchDialer(contact.primaryPhone) }
                    )
                }
            }
        }
    }
}
