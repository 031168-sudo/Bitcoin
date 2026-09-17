package com.bitcoinprice.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: BitcoinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                BitcoinPriceScreen(viewModel)
            }
        }
    }
}

@Composable
fun BitcoinPriceScreen(viewModel: BitcoinViewModel) {
    val state by viewModel.state.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (val s = state) {
            is BitcoinUiState.Loading -> LoadingContent()
            is BitcoinUiState.Error -> ErrorContent(s.message) { viewModel.refresh() }
            is BitcoinUiState.Success -> SuccessContent(s) { viewModel.refresh() }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ошибка загрузки данных", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
private fun SuccessContent(state: BitcoinUiState.Success, onRefresh: () -> Unit) {
    val timestampLabel = remember(state.fetchedAtMillis) {
        SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(state.fetchedAtMillis))
    }
    val changeColor = if (state.changePercent24h >= 0) Color(0xFF1E8E3E) else Color(0xFFD93025)
    val changeSign = if (state.changePercent24h >= 0) "+" else ""
    val sixMonthsAgo = (state.history.lastOrNull()?.timeSec ?: 0L) - 183L * 86400
    val recentHistory = state.history.filter { it.timeSec >= sixMonthsAgo }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Курс биткоина на $timestampLabel",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefresh) { Text("Обновить") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatUsd(state.priceUsd),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$changeSign${"%.2f".format(state.changePercent24h)}% за 24ч",
            color = changeColor,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Последние 6 месяцев",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (recentHistory.size >= 2) {
            PriceHistoryChart(
                points = recentHistory,
                modifier = Modifier.fillMaxWidth(),
                useLogScale = false,
                heightDp = 130.dp
            )
        } else {
            Text("Недостаточно данных", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "История с 2010 года по сегодня (логарифмическая шкала)",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (state.history.isNotEmpty()) {
            PriceHistoryChart(points = state.history, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Нет исторических данных", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatUsd(price: Double): String = "$" + "%,.2f".format(price)
