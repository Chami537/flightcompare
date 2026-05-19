package com.flightcompare.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSearch: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.searchId) {
        uiState.searchId?.let { onSearch(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlightCompare") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Origin with autocomplete ---
            var originExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = originExpanded && uiState.originSuggestions.isNotEmpty(),
                onExpandedChange = { originExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = uiState.origin,
                    onValueChange = {
                        viewModel.updateOrigin(it.uppercase())
                        originExpanded = true
                    },
                    label = { Text("From") },
                    placeholder = { Text("e.g. JFK, LAX") },
                    leadingIcon = { Icon(Icons.Default.FlightTakeoff, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                )

                ExposedDropdownMenu(
                    expanded = originExpanded && uiState.originSuggestions.isNotEmpty(),
                    onDismissRequest = { originExpanded = false },
                ) {
                    uiState.originSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${suggestion.code}  ${suggestion.city}",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = suggestion.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                viewModel.selectOrigin(suggestion)
                                originExpanded = false
                            },
                        )
                    }
                }
            }

            // Swap button
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        val dest = uiState.destination
                        viewModel.updateDestination(uiState.origin)
                        viewModel.updateOrigin(dest)
                    }
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Swap")
                }
            }

            // --- Destination with autocomplete ---
            var destinationExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = destinationExpanded && uiState.destinationSuggestions.isNotEmpty(),
                onExpandedChange = { destinationExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = uiState.destination,
                    onValueChange = {
                        viewModel.updateDestination(it.uppercase())
                        destinationExpanded = true
                    },
                    label = { Text("To") },
                    placeholder = { Text("e.g. LAX, JFK") },
                    leadingIcon = { Icon(Icons.Default.FlightLand, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                )

                ExposedDropdownMenu(
                    expanded = destinationExpanded && uiState.destinationSuggestions.isNotEmpty(),
                    onDismissRequest = { destinationExpanded = false },
                ) {
                    uiState.destinationSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${suggestion.code}  ${suggestion.city}",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = suggestion.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                viewModel.selectDestination(suggestion)
                                destinationExpanded = false
                            },
                        )
                    }
                }
            }

            // Departure Date
            OutlinedTextField(
                value = uiState.departureDate,
                onValueChange = viewModel::updateDepartureDate,
                label = { Text("Departure") },
                placeholder = { Text("YYYY-MM-DD") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Return Date
            OutlinedTextField(
                value = uiState.returnDate,
                onValueChange = viewModel::updateReturnDate,
                label = { Text("Return (optional)") },
                placeholder = { Text("YYYY-MM-DD") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Error
            uiState.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Search Button
            Button(
                onClick = viewModel::search,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Searching...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Flights")
                }
            }
        }
    }
}
