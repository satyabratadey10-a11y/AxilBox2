package com.axilbox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axilbox.app.model.VirtualInstance
import com.axilbox.app.ui.components.AboutDialog
import com.axilbox.app.ui.components.ConfirmDeleteDialog
import com.axilbox.app.ui.components.InstanceCard
import com.axilbox.app.ui.components.ResourceSummaryCard
import com.axilbox.app.ui.theme.AxilBoxTypography
import com.axilbox.app.ui.theme.BackgroundDark
import com.axilbox.app.ui.theme.BorderSubtle
import com.axilbox.app.ui.theme.PrimaryCyan
import com.axilbox.app.ui.theme.SurfacePrimary
import com.axilbox.app.ui.theme.SurfaceSecondary
import com.axilbox.app.ui.theme.TextMuted
import com.axilbox.app.ui.theme.TextPrimary
import com.axilbox.app.ui.theme.TextSecondary
import com.axilbox.app.ui.viewmodel.InstanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    viewModel: InstanceViewModel,
    onNavigateToAddInstance: () -> Unit,
    onNavigateToEditInstance: (Long) -> Unit,
    onNavigateToBootScreen: (Long) -> Unit
) {
    val uiState by viewModel.mainMenuUiState.collectAsState()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AxilBox",
                            style = AxilBoxTypography.titleLarge.copy(color = TextPrimary)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setAboutDialogVisible(true) }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "About",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddInstance,
                containerColor = PrimaryCyan,
                contentColor = TextPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add Instance") },
                text = { Text("New Instance", style = AxilBoxTypography.labelLarge) }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryCyan)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Host Resources Banner
                item {
                    uiState.systemResourceInfo?.let { resourceInfo ->
                        ResourceSummaryCard(
                            resourceInfo = resourceInfo,
                            activeGuestRamAllocatedMb = uiState.totalAllocatedGuestRamMb
                        )
                    }
                }

                // Search Bar (if instances exist)
                if (uiState.instances.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    "Search instances...",
                                    style = AxilBoxTypography.bodyMedium.copy(color = TextMuted)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = TextMuted
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfacePrimary,
                                unfocusedContainerColor = SurfacePrimary,
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Instances List Header
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Virtual Machines (${uiState.filteredInstances.size})",
                            style = AxilBoxTypography.titleSmall.copy(color = TextSecondary)
                        )
                    }
                }

                // Empty State
                if (uiState.filteredInstances.isEmpty()) {
                    item {
                        EmptyInstanceState(
                            hasSearchQuery = uiState.searchQuery.isNotBlank(),
                            onCreateClick = onNavigateToAddInstance
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredInstances,
                        key = { it.id }
                    ) { instance ->
                        InstanceCard(
                            instance = instance,
                            onBootClick = { onNavigateToBootScreen(instance.id) },
                            onEditClick = { onNavigateToEditInstance(instance.id) },
                            onDeleteClick = { viewModel.promptDeleteInstance(instance) }
                        )
                    }
                }

                // Bottom spacer for FAB clearance
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    // Modals
    uiState.selectedInstanceForDelete?.let { target ->
        ConfirmDeleteDialog(
            instance = target,
            onConfirm = { viewModel.confirmDeleteInstance() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }

    if (uiState.showAboutDialog) {
        AboutDialog(onDismiss = { viewModel.setAboutDialogVisible(false) })
    }
}

@Composable
private fun EmptyInstanceState(
    hasSearchQuery: Boolean,
    onCreateClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Devices,
            contentDescription = null,
            tint = PrimaryCyan.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasSearchQuery) "No matching instances found" else "No Virtual Instances Created",
            style = AxilBoxTypography.titleMedium.copy(color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasSearchQuery)
                "Try searching with a different instance name or OS profile."
            else
                "Create an isolated Android or Linux virtual instance to test apps, explore kernels, or practice systems programming.",
            style = AxilBoxTypography.bodyMedium.copy(color = TextMuted),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (!hasSearchQuery) {
            Spacer(modifier = Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = onCreateClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create First Instance", style = AxilBoxTypography.labelLarge)
            }
        }
    }
}
