package com.ryzix.vm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryzix.vm.ui.screens.CreateVMScreen
import com.ryzix.vm.ui.screens.DashboardScreen
import com.ryzix.vm.ui.screens.SettingsScreen
import com.ryzix.vm.ui.screens.VMDisplayScreen
import com.ryzix.vm.ui.theme.RyzixBackground
import com.ryzix.vm.ui.theme.RyzixVMTheme
import com.ryzix.vm.viewmodel.VMViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RyzixVMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RyzixBackground
                ) {
                    val navController = rememberNavController()
                    val viewModel: VMViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onStartVM = { config ->
                                    viewModel.startVM(config)
                                    navController.navigate("vm_display/${config.id}")
                                },
                                onCreateVM = { navController.navigate("create_vm") },
                                onSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("create_vm") {
                            CreateVMScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onCreated = { navController.popBackStack() }
                            )
                        }
                        composable("vm_display/{vmId}") { backStack ->
                            val vmId = backStack.arguments?.getString("vmId") ?: ""
                            val config = viewModel.vmList.value.find { it.id == vmId }
                                ?: viewModel.activeVM.value
                            if (config != null) {
                                VMDisplayScreen(
                                    viewModel = viewModel,
                                    config = config,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
