package com.example.ui.components

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.locale.AppStrings
import com.example.ui.locale.loc
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.util.BluetoothPrinterHelper

@Composable
fun BluetoothPrinterDialog(
    onDismissRequest: () -> Unit,
    onSelectPrinter: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    var devices by remember {
        mutableStateOf(BluetoothPrinterHelper.getPairedPrinters())
    }
    val savedAddress by remember {
        mutableStateOf(BluetoothPrinterHelper.getSavedPrinterAddress(context))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = GoldDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.printerDialogTitle(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = {
                    devices = BluetoothPrinterHelper.getPairedPrinters()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = GoldDark
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = loc(
                        en = "Select a printer. It will be saved as your default printer for fast 1-tap printing.",
                        gu = "પ્રિન્ટર પસંદ કરો. ઝડપી ૧-ટેપ પ્રિન્ટિંગ માટે તે તમારા ડિફૉલ્ટ પ્રિન્ટર તરીકે સાચવવામાં આવશે."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (devices.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = AppStrings.noPrintersFound(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = loc(
                                    en = "Please go to phone Settings -> Bluetooth and pair your thermal printer (58mm/80mm) first.",
                                    gu = "કૃપા કરીને ફોનના બ્લૂટૂથ સેટિંગ્સમાં જઈ તમારું થર્મલ પ્રિન્ટર પ્રથમ પેર કરો."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                    ) {
                        items(devices) { printer ->
                            val isSaved = printer.address == savedAddress
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        BluetoothPrinterHelper.savePreferredPrinter(
                                            context,
                                            printer.address,
                                            printer.name
                                        )
                                        onSelectPrinter(printer.device)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSaved) GoldLight.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSaved) GoldDark else GoldLight,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Default.Print,
                                                contentDescription = null,
                                                tint = if (isSaved) MaterialTheme.colorScheme.onPrimary else GoldDark,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = printer.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (isSaved) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = GoldDark
                                                ) {
                                                    Text(
                                                        text = loc(en = "Default", gu = "સાચવેલ"),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = printer.address,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(AppStrings.cancel())
            }
        }
    )
}
