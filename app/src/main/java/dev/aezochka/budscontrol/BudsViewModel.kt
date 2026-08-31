package dev.aezochka.budscontrol

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import dev.aezochka.budscontrol.device.BudsRepository
import dev.aezochka.budscontrol.proto.AncMode
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType

class BudsViewModel(private val repo: BudsRepository) : ViewModel() {

    val state = repo.state

    fun pairedDevices(): List<BluetoothDevice> = repo.pairedCandidates()
    fun connect(device: BluetoothDevice) = repo.connect(device)
    fun disconnect() = repo.disconnect()
    fun refresh() = repo.refresh()

    fun setAnc(mode: AncMode) = repo.setAncMode(mode)
    fun setAncCycle(modes: Set<AncMode>) = repo.setAncCycle(modes)
    fun setGameMode(on: Boolean) = repo.setGameMode(on)
    fun setMultipoint(on: Boolean) = repo.setMultipoint(on)
    fun setLdac(on: Boolean) = repo.setLdac(on)
    fun setTouch(side: TouchSide, type: TouchType, action: TouchAction) = repo.setTouch(side, type, action)
    fun findDevice(start: Boolean) = repo.findDevice(start)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudsApplication
                BudsViewModel(app.repository)
            }
        }
    }
}
