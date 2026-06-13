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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.zerobook.data.Party
import com.zerobook.data.Product
import com.zerobook.data.Voucher
import com.zerobook.data.ZeroBookSnapshot
import com.zerobook.data.emptyZeroBookSnapshot
import com.zerobook.database.bootstrapDatabase
import com.zerobook.database.openZeroBookDatabase
import com.zerobook.ui.rememberWindowInfo

enum class ZeroBookScreen(
    val label: String,
    val shortcut: String,
) {
    Dashboard("Dashboard", "1"),
    Vouchers("Vouchers", "2"),
    Parties("Parties", "3"),
    Products("Products", "4"),
    Reports("Reports", "5"),
    Settings("Settings", "6"),
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
    remember {
        bootstrapDatabase(openZeroBookDatabase())
    }
    val snapshot = remember { emptyZeroBookSnapshot() }
    val isDesktop = platform == "desktop"
    val backStack = remember { mutableStateListOf<ZeroBookScreen>() }
    var screen by remember { mutableStateOf(ZeroBookScreen.Dashboard) }

    MaterialTheme {
        Surface(color = Color(0xFFFDF6EC), modifier = Modifier.fillMaxSize()) {
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
                        .background(Color(0xFFFDF6EC))
                        .then(shortcutModifier),
                ) {
                    DesktopSidebar(
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
                    containerColor = Color(0xFFFDF6EC),
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
@Composable
private fun DesktopSidebar(
    current: ZeroBookScreen,
    onNavigate: (ZeroBookScreen) -> Unit,
) {
    val windowInfo = rememberWindowInfo()
    val sidebarExpanded = windowInfo.widthDp >= 840.dp

    NavigationRail(
        modifier = Modifier.width(if (sidebarExpanded) 240.dp else 60.dp),
        containerColor = Color(0xFFFFE8D2),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (sidebarExpanded) "ZeroBook" else "ZB",
            color = Color(0xFF8C4E24),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        navItems.forEach { item ->
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text("${item.screen.label}  (Alt+${item.screen.shortcut})")
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
                    label = if (sidebarExpanded) {
                        { Text(item.screen.label) }
                    } else {
                        null
                    },
                    alwaysShowLabel = sidebarExpanded,
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
            .padding(24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dashboard", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8C4E24))
                Text(
                    listOf(snapshot.profile.businessName, snapshot.profile.fyLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                        .ifBlank { "No business profile saved" },
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
            SectionCard(title = "Sales vs Purchases") {
                EmptyState("Chart will appear after vouchers are posted")
            }
        }
        item {
            SectionCard(title = "Cash Flow") {
                EmptyState("30 day cash flow appears after transactions are saved")
            }
        }
        item {
            SectionCard(title = "Recent Transactions") {
                EmptyState("No transactions yet")
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
        if (vouchers.isEmpty()) {
            EmptyState("No vouchers yet")
        } else {
            vouchers.forEach { voucher ->
                TwoLineRow(
                    title = voucher.voucherNo,
                    subtitle = "${voucher.partyName} • ${voucher.type} • ${voucher.status}",
                    trailing = formatAmount(voucher.netAmount),
                )
            }
        }
    }
}

@Composable
private fun PartiesScreen(parties: List<Party>) {
    CollectionScreen(
        title = "Parties",
        subtitle = "Customers and suppliers with live balances",
    ) {
        if (parties.isEmpty()) {
            EmptyState("No parties yet")
        } else {
            parties.forEach { party ->
                TwoLineRow(
                    title = party.name,
                    subtitle = "${party.type} • ${party.city} • ${party.phone}",
                    trailing = "${party.balanceType} ${formatAmount(party.balance)}",
                )
            }
        }
    }
}

@Composable
private fun ProductsScreen(products: List<Product>) {
    CollectionScreen(
        title = "Products",
        subtitle = "Rates, stock, and GST setup",
    ) {
        if (products.isEmpty()) {
            EmptyState("No products yet")
        } else {
            products.forEach { product ->
                TwoLineRow(
                    title = product.name,
                    subtitle = "${product.unit} • GST ${product.gstRate.toInt()}% • Stock ${product.currentStock}",
                    trailing = formatAmount(product.saleRate),
                )
            }
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
        TwoLineRow("Business Name", profile.businessName.ifBlank { "Not set" }, "")
        TwoLineRow("Owner", profile.ownerName.ifBlank { "Not set" }, "")
        TwoLineRow("Phone", profile.phone.ifBlank { "Not set" }, "")
        TwoLineRow("Email", profile.email.ifBlank { "Not set" }, "")
        TwoLineRow("Financial Year", profile.fyLabel.ifBlank { "Not set" }, "")
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
            .padding(24.dp)
            .imePadding(),
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
private fun EmptyState(message: String) {
    Text(message, color = Color(0xFF8D6B55))
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

private fun formatAmount(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    val sign = if (rounded < 0) "-" else ""
    val absoluteText = kotlin.math.abs(rounded).toString()
    val parts = absoluteText.split(".")
    val whole = parts.firstOrNull().orEmpty()
    val decimals = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
    return "$sign₹${formatIndianDigits(whole)}.$decimals"
}

private fun formatIndianDigits(value: String): String {
    if (value.length <= 3) return value
    val lastThree = value.takeLast(3)
    val remaining = value.dropLast(3)
    val grouped = remaining
        .reversed()
        .chunked(2)
        .joinToString(",") { it.reversed() }
        .split(",")
        .reversed()
        .joinToString(",")
    return "$grouped,$lastThree"
}
