package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.LocalLanguageManager
import com.example.ui.screens.*
import com.example.ui.theme.JewelleryTheme
import com.example.ui.viewmodel.JewelleryViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: JewelleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsState()
            val languageManager = remember { LanguageManager(AppLanguage.ENGLISH) }

            LaunchedEffect(settings?.language) {
                val code = settings?.language ?: "en"
                val appLang = AppLanguage.fromCode(code)
                languageManager.currentLanguage = appLang
                LanguageManager.globalLanguage = appLang
            }

            CompositionLocalProvider(LocalLanguageManager provides languageManager) {
                JewelleryTheme {
                    val navController = rememberNavController()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val currentAccount by viewModel.currentAccount.collectAsState()
                    val userMessage by viewModel.userMessage.collectAsState()

                LaunchedEffect(userMessage) {
                    userMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearMessage()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onSplashFinished = {
                                    val destination = if (viewModel.currentAccount.value != null) "dashboard" else "login"
                                    navController.navigate(destination) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToRegister = {
                                    navController.navigate("registration")
                                },
                                onNavigateToForgotPassword = {
                                    navController.navigate("forgot_password")
                                }
                            )
                        }

                        composable("registration") {
                            RegistrationScreen(
                                viewModel = viewModel,
                                onNavigateToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("registration") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("forgot_password") {
                            ForgotPasswordScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onResetSuccess = {
                                    navController.navigate("login") {
                                        popUpTo("forgot_password") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToCreateBill = { billType ->
                                    navController.navigate("create_bill?type=$billType")
                                },
                                onNavigateToBillHistory = {
                                    navController.navigate("bill_history")
                                },
                                onNavigateToStatement = {
                                    navController.navigate("statement")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "create_bill?type={type}",
                            arguments = listOf(navArgument("type") {
                                type = NavType.StringType
                                defaultValue = "SALE"
                            })
                        ) { backStackEntry ->
                            val billType = backStackEntry.arguments?.getString("type") ?: "SALE"
                            CreateBillScreen(
                                viewModel = viewModel,
                                editingBillId = null,
                                initialBillType = billType,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onBillSaved = { billId ->
                                    navController.navigate("bill_preview/$billId") {
                                        popUpTo("create_bill?type=$billType") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("create_bill") {
                            CreateBillScreen(
                                viewModel = viewModel,
                                editingBillId = null,
                                initialBillType = "SALE",
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onBillSaved = { billId ->
                                    navController.navigate("bill_preview/$billId") {
                                        popUpTo("create_bill") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "edit_bill/{billId}",
                            arguments = listOf(navArgument("billId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val billId = backStackEntry.arguments?.getString("billId")
                            CreateBillScreen(
                                viewModel = viewModel,
                                editingBillId = billId,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onBillSaved = { savedId ->
                                    navController.navigate("bill_preview/$savedId") {
                                        popUpTo("edit_bill/$savedId") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "bill_preview/{billId}",
                            arguments = listOf(navArgument("billId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val billId = backStackEntry.arguments?.getString("billId") ?: ""
                            BillPreviewScreen(
                                billId = billId,
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToEditBill = { editId ->
                                    navController.navigate("edit_bill/$editId")
                                },
                                onBillDeleted = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("bill_history") {
                            BillHistoryScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToBillPreview = { billId ->
                                    navController.navigate("bill_preview/$billId")
                                }
                            )
                        }

                        composable("statement") {
                            StatementScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onAccountDeleted = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
