package com.zerobook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerobook.data.DashboardMetric
import com.zerobook.data.Expense
import com.zerobook.data.Party
import com.zerobook.data.Product
import com.zerobook.data.SampleData
import com.zerobook.data.Voucher
import com.zerobook.data.ZeroBookSnapshot

enum class ZeroBookScreen(
    val label: String,
    val shortcut: String,
) {
    Dashboard("Dashboard", "Alt+1"),
    Vouchers("Vouchers", "Alt+2"),
    Parties("Parties", "Alt+3"),
    Products("Products", "Alt+4"),
    Reports("Reports", "Alt+5"),
    Settings("Settings", "Alt+6"),
}

private data class NavItem(
    val screen: ZeroBookScreen,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(ZeroBookScreen.Dashboard, Icons.Outlined.Home),
    NavItem(ZeroBookScreen.Vouchers, Icons.AutoMirrored.Outlined.ReceiptLong),
    NavItem(ZeroBookScreen.Parties, Icons.Outlined.People),
    NavItem(ZeroBookScreen.Products, Icons.Outlined.Inventory2),
    NavItem(ZeroBookScreen.Reports, Icons.Outlined.BarChart),
    NavItem(ZeroBookScreen.Settings, Icons.Outlined.Settings),
)

private fun shortcutDestination(key: Key): ZeroBookScreen? = when (key) {
    Key.One -> ZeroBookScreen.Dashboard
    Key.Two -> ZeroBookScreen.Vouchers
    Key.Three -> ZeroBookScreen.Parties
    Key.Four -> ZeroBookScreen.Products
    Key.Five -> ZeroBookScreen.Reports
    Key.Six -> ZeroBookScreen.Settings
    Key.S -> ZeroBookScreen.Vouchers
    Key.P -> ZeroBookScreen.Products
    Key.R -> ZeroBookScreen.Reports
    else -> null
}

fun handleShortcut(
    event: KeyEvent,
    current: ZeroBookScreen,
    backStack: MutableList<ZeroBookScreen>,
    onNavigate: (ZeroBookScreen) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.isAltPressed) {
        val destination = shortcutDestination(event.key) ?: return false
        if (destination != current) {
            backStack += current
            onNavigate(destination)
        }
        return true
    }

    if (event.key == Key.Escape && backStack.isNotEmpty()) {
        onNavigate(backStack.removeLast())
        return true
    }

    return false
}

@Composable
fun App(platform: String = "android") {
    val snapshot = remember { SampleData.snapshot() }
    val isDesktop = platform == "desktop"
    val backStack = remember { mutableStateListOf<ZeroBookScreen>() }
    var screen by remember { mutableStateOf(ZeroBookScreen.Dashboard) }

    MaterialTheme {
        Surface(color = Color(0xFFFFF6EE), modifier = Modifier.fillMaxSize()) {
            val shortcutModifier = Modifier.onPreviewKeyEvent { event ->
                handleShortcut(
                    event = event,
                    current = screen,
                    backStack = backStack,
                    onNavigate = { screen = it },
                )
            }

            if (isDesktop) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFFF6EE))
                        .then(shortcutModifier),
                ) {
                    DesktopRail(
                        current = screen,
                        onNavigate = { destination ->
                            if (destination != screen) {
                                backStack += screen
                                screen = destination
                            }
                        },
                    )
                    VerticalDivider(color = Color(0xFFE9D9C8))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                    ) {
                        ScreenContent(screen = screen, snapshot = snapshot)
                    }
                }
            } else {
                Scaffold(
                    containerColor = Color(0xFFFFF6EE),
                    modifier = shortcutModifier,
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFFFFE8D2)) {
                            navItems.forEach { item ->
                                NavigationBarItem(
                                    selected = screen == item.screen,
                                    onClick = {
                                        if (item.screen != screen) {
                                            backStack += screen
                                            screen = item.screen
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.screen.label) },
                                    label = { Text(item.screen.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        ScreenContent(screen = screen, snapshot = snapshot)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun DesktopRail(
    current: ZeroBookScreen,
    onNavigate: (ZeroBookScreen) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.width(72.dp),
        containerColor = Color(0xFFFFE8D2),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "ZB",
            color = Color(0xFF8C4E24),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        navItems.forEach { item ->
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text("${item.screen.label} (${item.screen.shortcut})")
                    }
                },
                state = rememberTooltipState(),
            ) {
                NavigationRailItem(
                    selected = current == item.screen,
                    onClick = { onNavigate(item.screen) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.screen.label,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = null,
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    screen: ZeroBookScreen,
    snapshot: ZeroBookSnapshot,
) {
    when (screen) {
        ZeroBookScreen.Dashboard -> DashboardScreen(snapshot)
        ZeroBookScreen.Vouchers -> VouchersScreen(snapshot.vouchers)
        ZeroBookScreen.Parties -> PartiesScreen(snapshot.parties)
        ZeroBookScreen.Products -> ProductsScreen(snapshot.products)
        ZeroBookScreen.Reports -> ReportsScreen(snapshot)
        ZeroBookScreen.Settings -> SettingsScreen(snapshot)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardScreen(snapshot: ZeroBookSnapshot) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dashboard", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8C4E24))
                Text(
                    "${snapshot.profile.businessName} • ${snapshot.profile.city}, ${snapshot.profile.state}",
                    color = Color(0xFF8D6B55),
                )
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = 2,
            ) {
                snapshot.metrics.forEach { metric ->
                    MetricCard(metric)
                }
            }
        }
        item {
            SectionCard(title = "Recent Vouchers") {
                snapshot.vouchers.forEach { voucher ->
                    TwoLineRow(
                        title = "${voucher.voucherNo} • ${voucher.partyName}",
                        subtitle = "${voucher.type} • ${voucher.dateLabel}",
                        trailing = "Rs ${voucher.netAmount.toInt()}",
                    )
                }
            }
        }
        item {
            SectionCard(title = "Expenses Overview") {
                snapshot.expenses.forEach { expense ->
                    TwoLineRow(
                        title = expense.category,
                        subtitle = "${expense.description} • ${expense.dateLabel}",
                        trailing = "Rs ${expense.amount.toInt()}",
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(metric: DashboardMetric) {
    Card(
        modifier = Modifier.width(280.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(metric.label, color = Color(0xFF8D6B55), fontSize = 13.sp)
            Text(metric.value, color = Color(0xFF8C4E24), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(metric.supportingText, color = Color(0xFFB18A72), fontSize = 12.sp)
        }
    }
}

@Composable
private fun VouchersScreen(vouchers: List<Voucher>) {
    CollectionScreen(
        title = "Vouchers",
        subtitle = "Sales, purchases, receipts, and invoice activity",
    ) {
        vouchers.forEach { voucher ->
            TwoLineRow(
                title = voucher.voucherNo,
                subtitle = "${voucher.partyName} • ${voucher.type} • ${voucher.status}",
                trailing = "Rs ${voucher.netAmount.toInt()}",
            )
        }
    }
}

@Composable
private fun PartiesScreen(parties: List<Party>) {
    CollectionScreen(
        title = "Parties",
        subtitle = "Customers and suppliers with live balances",
    ) {
        parties.forEach { party ->
            TwoLineRow(
                title = party.name,
                subtitle = "${party.type} • ${party.city} • ${party.phone}",
                trailing = "${party.balanceType} Rs ${party.balance.toInt()}",
            )
        }
    }
}

@Composable
private fun ProductsScreen(products: List<Product>) {
    CollectionScreen(
        title = "Products",
        subtitle = "Rates, stock, and GST setup",
    ) {
        products.forEach { product ->
            TwoLineRow(
                title = product.name,
                subtitle = "${product.unit} • GST ${product.gstRate.toInt()}% • Stock ${product.currentStock}",
                trailing = "Rs ${product.saleRate.toInt()}",
            )
        }
    }
}

@Composable
private fun ReportsScreen(snapshot: ZeroBookSnapshot) {
    CollectionScreen(
        title = "Reports",
        subtitle = "High-level business trends and reporting shortcuts",
    ) {
        snapshot.metrics.forEach { metric ->
            TwoLineRow(
                title = metric.label,
                subtitle = metric.supportingText,
                trailing = metric.value,
            )
        }
    }
}

@Composable
private fun SettingsScreen(snapshot: ZeroBookSnapshot) {
    CollectionScreen(
        title = "Settings",
        subtitle = "Business profile, communication, and compliance defaults",
    ) {
        val profile = snapshot.profile
        TwoLineRow("Business Name", profile.businessName, "")
        TwoLineRow("Owner", profile.ownerName, "")
        TwoLineRow("Phone", profile.phone, "")
        TwoLineRow("Email", profile.email, "")
        TwoLineRow("Financial Year", profile.fyLabel, "")
    }
}

@Composable
private fun CollectionScreen(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8C4E24))
                Text(subtitle, color = Color(0xFF8D6B55))
            }
        }
        item {
            SectionCard(title = title) {
                content()
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    body: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF8C4E24))
            body()
        }
    }
}

@Composable
private fun TwoLineRow(
    title: String,
    subtitle: String,
    trailing: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color(0xFF8C4E24), fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color(0xFF8D6B55), fontSize = 13.sp)
            }
        }
        if (trailing.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, color = Color(0xFF8C4E24), fontWeight = FontWeight.SemiBold)
        }
    }
}
