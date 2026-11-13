package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOutUser
import com.example.data.model.CurrencyCode
import com.example.data.model.TransactionEntity
import com.example.ui.DataStatus
import com.example.ui.PulseTab
import com.example.ui.PulseUiState
import com.example.ui.PulseViewModel
import com.example.ui.components.BudgetsSection
import com.example.ui.components.CashflowChart
import com.example.ui.components.CategoryDonut
import com.example.ui.components.KpiBentoRow
import com.example.ui.components.TransactionModalSheet
import com.example.ui.components.TransactionsCard
import com.example.ui.theme.LocalPulseColors
import com.example.util.FormatUtils

private data class NavDestination(
  val tab: PulseTab,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

private val NAV_DESTINATIONS = listOf(
  NavDestination(PulseTab.OVERVIEW, R.string.tab_overview, Icons.Rounded.SpaceDashboard, Icons.Outlined.SpaceDashboard),
  NavDestination(PulseTab.ACTIVITY, R.string.tab_activity, Icons.Rounded.FormatListBulleted, Icons.Outlined.FormatListBulleted),
  NavDestination(PulseTab.ANALYTICS, R.string.tab_analytics, Icons.Rounded.PieChart, Icons.Outlined.PieChart),
  NavDestination(PulseTab.BUDGETS, R.string.tab_budgets, Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
)

@Composable
fun DashboardScreen(
  viewModel: PulseViewModel,
  userEmail: String?,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  var modalOpen by remember { mutableStateOf(false) }
  var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }

  // Back button on secondary tabs navigates back to Overview
  BackHandler(enabled = uiState.selectedTab != PulseTab.OVERVIEW) {
    viewModel.selectTab(PulseTab.OVERVIEW)
  }

  LaunchedEffect(uiState.snackbarMessage) {
    val msg = uiState.snackbarMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearSnackbar()
    }
  }

  val csvExportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/csv"),
  ) { uri ->
    if (uri != null) {
      runCatching {
        val csv = viewModel.buildCsvExport()
        context.contentResolver.openOutputStream(uri)?.use { stream ->
          stream.write(csv.toByteArray(Charsets.UTF_8))
        }
      }.onSuccess {
        viewModel.showMessage("Exported CSV successfully")
      }.onFailure {
        Toast.makeText(context, "Failed to export CSV", Toast.LENGTH_SHORT).show()
      }
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWide = maxWidth >= 768.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (isWide) {
        PulseNavigationRail(
          selectedTab = uiState.selectedTab,
          onSelectTab = viewModel::selectTab,
          onAddClick = {
            editingTx = null
            modalOpen = true
          },
        )
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          PulseTopBar(
            selectedMonth = uiState.selectedMonth,
            currency = uiState.currency,
            isDarkTheme = isDarkTheme,
            userEmail = userEmail,
            showLogo = !isWide,
            onStepMonth = viewModel::stepMonth,
            onToggleAllTime = viewModel::toggleAllTimeMonth,
            onSelectCurrency = viewModel::setCurrency,
            onExportCsv = {
              val suffix = uiState.selectedMonth.ifBlank { "all" }
              csvExportLauncher.launch("expense-tracker-an-cloud-$suffix.csv")
            },
            onToggleTheme = { viewModel.toggleDarkMode(isDarkTheme) },
            onSignOut = {
              signOutUser(credentialManager = credentialManager, scope = scope)
            },
          )
        },
        bottomBar = {
          if (!isWide) {
            NavigationBar(
              windowInsets = WindowInsets.navigationBars,
              containerColor = MaterialTheme.colorScheme.surface,
            ) {
              NAV_DESTINATIONS.forEach { dest ->
                val selected = uiState.selectedTab == dest.tab
                NavigationBarItem(
                  selected = selected,
                  onClick = { viewModel.selectTab(dest.tab) },
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = stringResource(dest.labelRes),
                    )
                  },
                  label = { Text(stringResource(dest.labelRes)) },
                  modifier = Modifier.testTag("nav_${dest.tab.name.lowercase()}"),
                )
              }
            }
          }
        },
        floatingActionButton = {
          if (!isWide) {
            FloatingActionButton(
              onClick = {
                editingTx = null
                modalOpen = true
              },
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.testTag("fab_add_tx"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_transaction),
              )
            }
          }
        },
      ) { innerPadding ->
        DashboardBody(
          uiState = uiState,
          isWide = isWide,
          onSaveBudget = viewModel::saveBudget,
          onApplySuggestedBudgets = viewModel::applySuggestedBudgets,
          onSearchChange = viewModel::setSearchQuery,
          onFilterTypeChange = viewModel::setFilterType,
          onEditTx = { tx ->
            editingTx = tx
            modalOpen = true
          },
          onDeleteTx = { tx -> viewModel.deleteTransaction(tx.id) },
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        )
      }
    }
  }

  if (modalOpen) {
    TransactionModalSheet(
      initialTx = editingTx,
      isSaving = uiState.isMutating,
      onDismiss = {
        modalOpen = false
        editingTx = null
      },
      onSave = { id, title, amount, type, category, date, note ->
        viewModel.saveTransaction(id, title, amount, type, category, date, note)
      },
    )
  }
}

@Composable
private fun PulseNavigationRail(
  selectedTab: PulseTab,
  onSelectTab: (PulseTab) -> Unit,
  onAddClick: () -> Unit,
) {
  val pulseColors = LocalPulseColors.current
  NavigationRail(
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier
      .fillMaxHeight()
      .width(210.dp)
      .statusBarsPadding(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .padding(horizontal = 14.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Brush.linearGradient(listOf(pulseColors.heroStart, pulseColors.heroEnd))),
            contentAlignment = Alignment.Center,
          ) {
            Text("E", color = Color.White, fontWeight = FontWeight.Bold)
          }
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text("Expense Intelligence", style = MaterialTheme.typography.labelSmall, color = pulseColors.mutedText)
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        NAV_DESTINATIONS.forEach { dest ->
          val selected = selectedTab == dest.tab
          NavigationRailItem(
            selected = selected,
            onClick = { onSelectTab(dest.tab) },
            icon = {
              Icon(
                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                contentDescription = stringResource(dest.labelRes),
              )
            },
            label = { Text(stringResource(dest.labelRes)) },
            modifier = Modifier.testTag("rail_${dest.tab.name.lowercase()}"),
          )
        }
      }

      Button(
        onClick = onAddClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .minimumInteractiveComponentSize()
          .testTag("rail_add_tx"),
      ) {
        Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.add_transaction), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun PulseTopBar(
  selectedMonth: String,
  currency: CurrencyCode,
  isDarkTheme: Boolean,
  userEmail: String?,
  showLogo: Boolean,
  onStepMonth: (Int) -> Unit,
  onToggleAllTime: () -> Unit,
  onSelectCurrency: (CurrencyCode) -> Unit,
  onExportCsv: () -> Unit,
  onToggleTheme: () -> Unit,
  onSignOut: () -> Unit,
) {
  val pulseColors = LocalPulseColors.current
  var currencyMenuOpen by remember { mutableStateOf(false) }
  var accountMenuOpen by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
    tonalElevation = 1.dp,
  ) {
    Column(modifier = Modifier.statusBarsPadding()) {
      if (showLogo) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier
              .weight(1f)
              .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(pulseColors.heroStart, pulseColors.heroEnd))),
              contentAlignment = Alignment.Center,
            ) {
              Text("E", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          TopBarActionsRow(
            userEmail = userEmail,
            isDarkTheme = isDarkTheme,
            accountMenuOpen = accountMenuOpen,
            onToggleAccountMenu = { accountMenuOpen = it },
            onExportCsv = onExportCsv,
            onToggleTheme = onToggleTheme,
            onSignOut = onSignOut,
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          MonthStepperControl(
            selectedMonth = selectedMonth,
            onStepMonth = onStepMonth,
            onToggleAllTime = onToggleAllTime,
          )
          CurrencySelectorControl(
            currency = currency,
            menuOpen = currencyMenuOpen,
            onToggleMenu = { currencyMenuOpen = it },
            onSelectCurrency = onSelectCurrency,
          )
        }
      } else {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = FormatUtils.formatMonthLabel(selectedMonth),
            style = MaterialTheme.typography.titleLarge,
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            MonthStepperControl(
              selectedMonth = selectedMonth,
              onStepMonth = onStepMonth,
              onToggleAllTime = onToggleAllTime,
            )
            CurrencySelectorControl(
              currency = currency,
              menuOpen = currencyMenuOpen,
              onToggleMenu = { currencyMenuOpen = it },
              onSelectCurrency = onSelectCurrency,
            )
            TopBarActionsRow(
              userEmail = userEmail,
              isDarkTheme = isDarkTheme,
              accountMenuOpen = accountMenuOpen,
              onToggleAccountMenu = { accountMenuOpen = it },
              onExportCsv = onExportCsv,
              onToggleTheme = onToggleTheme,
              onSignOut = onSignOut,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MonthStepperControl(
  selectedMonth: String,
  onStepMonth: (Int) -> Unit,
  onToggleAllTime: () -> Unit,
) {
  val pulseColors = LocalPulseColors.current
  Surface(
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, pulseColors.cardBorder),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = { onStepMonth(-1) },
        modifier = Modifier
          .size(36.dp)
          .minimumInteractiveComponentSize()
          .testTag("month_prev"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
          contentDescription = "Previous month",
          modifier = Modifier.size(18.dp),
        )
      }
      TextButton(
        onClick = onToggleAllTime,
        modifier = Modifier.testTag("month_toggle_all"),
      ) {
        Text(
          text = FormatUtils.formatMonthLabel(selectedMonth),
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
        )
      }
      IconButton(
        onClick = { onStepMonth(1) },
        modifier = Modifier
          .size(36.dp)
          .minimumInteractiveComponentSize()
          .testTag("month_next"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = "Next month",
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun CurrencySelectorControl(
  currency: CurrencyCode,
  menuOpen: Boolean,
  onToggleMenu: (Boolean) -> Unit,
  onSelectCurrency: (CurrencyCode) -> Unit,
) {
  val pulseColors = LocalPulseColors.current
  Box {
    Surface(
      onClick = { onToggleMenu(true) },
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, pulseColors.cardBorder),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("currency_selector"),
    ) {
      Text(
        text = currency.code,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      )
    }
    DropdownMenu(
      expanded = menuOpen,
      onDismissRequest = { onToggleMenu(false) },
    ) {
      CurrencyCode.entries.forEach { code ->
        DropdownMenuItem(
          text = { Text("${code.symbol} ${code.code}") },
          onClick = {
            onSelectCurrency(code)
            onToggleMenu(false)
          },
        )
      }
    }
  }
}

@Composable
private fun TopBarActionsRow(
  userEmail: String?,
  isDarkTheme: Boolean,
  accountMenuOpen: Boolean,
  onToggleAccountMenu: (Boolean) -> Unit,
  onExportCsv: () -> Unit,
  onToggleTheme: () -> Unit,
  onSignOut: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    IconButton(
      onClick = onExportCsv,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("export_csv_button"),
    ) {
      Icon(
        imageVector = Icons.Outlined.Download,
        contentDescription = stringResource(R.string.export_csv),
      )
    }

    IconButton(
      onClick = onToggleTheme,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("toggle_theme_button"),
    ) {
      Icon(
        imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
        contentDescription = stringResource(R.string.toggle_theme),
      )
    }

    Box {
      IconButton(
        onClick = { onToggleAccountMenu(true) },
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("account_menu_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.AccountCircle,
          contentDescription = "Account menu",
        )
      }
      DropdownMenu(
        expanded = accountMenuOpen,
        onDismissRequest = { onToggleAccountMenu(false) },
      ) {
        if (!userEmail.isNullOrBlank()) {
          DropdownMenuItem(
            text = { Text(userEmail, style = MaterialTheme.typography.bodySmall) },
            onClick = {},
            enabled = false,
          )
        }
        DropdownMenuItem(
          text = { Text(stringResource(R.string.sign_out)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Logout,
              contentDescription = null,
            )
          },
          onClick = {
            onToggleAccountMenu(false)
            onSignOut()
          },
        )
      }
    }
  }
}

@Composable
private fun DashboardBody(
  uiState: PulseUiState,
  isWide: Boolean,
  onSaveBudget: (String, Double) -> Unit,
  onApplySuggestedBudgets: () -> Unit,
  onSearchChange: (String) -> Unit,
  onFilterTypeChange: (com.example.ui.TransactionFilterType) -> Unit,
  onEditTx: (TransactionEntity) -> Unit,
  onDeleteTx: (TransactionEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val pulseColors = LocalPulseColors.current

  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 1100.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      when (val status = uiState.dataStatus) {
        is DataStatus.Loading -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
        is DataStatus.Error -> {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = pulseColors.expense.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = status.message,
              color = pulseColors.expense,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
        is DataStatus.Ready -> {
          if (uiState.selectedTab == PulseTab.OVERVIEW || uiState.selectedTab == PulseTab.ANALYTICS) {
            KpiBentoRow(
              summary = uiState.summary,
              currency = uiState.currency,
              isWide = isWide,
            )

            if (isWide) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                ChartBentoCard(
                  title = stringResource(R.string.cashflow_trend),
                  modifier = Modifier.weight(1f),
                ) {
                  CashflowChart(
                    dailyTrend = uiState.summary.dailyTrend,
                    currency = uiState.currency,
                  )
                }
                ChartBentoCard(
                  title = stringResource(R.string.spending_by_category),
                  modifier = Modifier.weight(1f),
                ) {
                  CategoryDonut(
                    breakdown = uiState.summary.categoryBreakdown,
                    totalExpense = uiState.summary.totalExpense,
                    currency = uiState.currency,
                  )
                }
              }
            } else {
              ChartBentoCard(title = stringResource(R.string.cashflow_trend)) {
                CashflowChart(
                  dailyTrend = uiState.summary.dailyTrend,
                  currency = uiState.currency,
                )
              }
              ChartBentoCard(title = stringResource(R.string.spending_by_category)) {
                CategoryDonut(
                  breakdown = uiState.summary.categoryBreakdown,
                  totalExpense = uiState.summary.totalExpense,
                  currency = uiState.currency,
                )
              }
            }
          }

          if (uiState.selectedTab == PulseTab.BUDGETS) {
            BudgetsSection(
              budgetStatuses = uiState.summary.budgetStatuses,
              currency = uiState.currency,
              isWide = isWide,
              onSaveBudget = onSaveBudget,
              onApplySuggested = onApplySuggestedBudgets,
            )
          }

          if (uiState.selectedTab == PulseTab.OVERVIEW || uiState.selectedTab == PulseTab.ACTIVITY) {
            TransactionsCard(
              transactions = uiState.filteredTransactions,
              currency = uiState.currency,
              searchQuery = uiState.searchQuery,
              filterType = uiState.filterType,
              onSearchChange = onSearchChange,
              onFilterTypeChange = onFilterTypeChange,
              onEditClick = onEditTx,
              onDeleteClick = onDeleteTx,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(72.dp))
    }
  }
}

@Composable
private fun ChartBentoCard(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val pulseColors = LocalPulseColors.current
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, pulseColors.cardBorder),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      content()
    }
  }
}
