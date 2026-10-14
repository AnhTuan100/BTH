package com.example.baith2.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.baith2.component.ContactItem
import com.example.baith2.viewmodel.ContactViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    viewModel: ContactViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val contacts by viewModel.contactsLiveData.observeAsState(emptyList())
    val syncResult by viewModel.syncResult.collectAsState()

    val filteredContacts = remember(contacts, searchQuery) {
        viewModel.filteredContacts()
    }

    // ── Runtime permission launcher ───────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        val writeGranted = permissions[Manifest.permission.WRITE_CONTACTS] == true
        if (readGranted && writeGranted) {
            viewModel.syncWithSystemContacts()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Cần cấp quyền đọc và ghi Danh bạ để đồng bộ",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    // ── Hiển thị kết quả sync ─────────────────────────────────────────────────
    LaunchedEffect(syncResult.state) {
        when (syncResult.state) {
            ContactViewModel.SyncState.SUCCESS -> {
                snackbarHostState.showSnackbar(
                    message = "Đồng bộ thành công! " +
                            "Nhập: ${syncResult.imported} | Xuất: ${syncResult.exported}",
                    duration = SnackbarDuration.Short
                )
                viewModel.resetSyncResult()
            }
            ContactViewModel.SyncState.ERROR -> {
                snackbarHostState.showSnackbar(
                    message = "Lỗi đồng bộ: ${syncResult.errorMessage}",
                    duration = SnackbarDuration.Long
                )
                viewModel.resetSyncResult()
            }
            else -> {}
        }
    }

    fun launchDialer(phone: String) {
        context.startActivity(
            Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") }
        )
    }

    fun onSyncClick() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            )
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Danh Bạ") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Nút đồng bộ với Danh bạ hệ thống
                    IconButton(
                        onClick = { onSyncClick() },
                        enabled = syncResult.state != ContactViewModel.SyncState.SYNCING
                    ) {
                        if (syncResult.state == ContactViewModel.SyncState.SYNCING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Đồng bộ Danh bạ hệ thống",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
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

