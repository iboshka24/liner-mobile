package dev.lyra.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.lyra.player.player.PlayerViewModel
import dev.lyra.player.ui.LyraRoot
import dev.lyra.player.ui.LyraTheme

class MainActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels { AppFactory(application) }
    private val libraryViewModel: LibraryViewModel by viewModels { AppFactory(application) }
    private val searchViewModel: SearchViewModel by viewModels { AppFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LyraTheme {
                LyraRoot(playerViewModel, libraryViewModel, searchViewModel)
            }
        }
    }
}

private class AppFactory(private val app: android.app.Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val model = when (modelClass) {
            PlayerViewModel::class.java -> PlayerViewModel(app)
            LibraryViewModel::class.java -> LibraryViewModel(app)
            SearchViewModel::class.java -> SearchViewModel(app)
            else -> error("Unknown model ${modelClass.name}")
        }
        return model as T
    }
}
