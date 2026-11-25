package ro.pub.cs.systems.eim.bluetoothchatapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var chatArrayAdapter: ArrayAdapter<String>
    private var chatMessages = mutableListOf<String>()

    private var selectedDevice: BluetoothDevice? = null

    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var listDevicesButton: Button

    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    companion object {
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSIONS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO 1: Initialize views
        initViews()

        // TODO 2: Initialize Bluetooth
        initBluetooth()

        // TODO 3: Request permissions
        checkPermissions()

        // TODO 6: Start server socket for incoming connections
        startServer()

        // TODO 7: Send message using ConnectedThread
        sendMessage()
    }

    // TODO 1: Implement the method that initializes the views
    private fun initViews() {
        val chatListView = findViewById<ListView>(R.id.chatListView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        listDevicesButton = findViewById(R.id.listDevicesButton)

        listDevicesButton.setOnClickListener {
            listPairedDevices()
        }

        chatArrayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            chatMessages
        )
        chatListView.adapter = chatArrayAdapter
    }

    // TODO 2: Implement the method that initializes Bluetooth
    private fun initBluetooth() {
        val manager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        val adapter = manager?.adapter

        if (adapter == null) {
            Toast.makeText(this, "Bluetooth indisponibil.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bluetoothAdapter = adapter
    }


    // TODO 3: Implement the method that checks permissions
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            val missingPermissions = requiredPermissions.filter { permission ->
                ActivityCompat.checkSelfPermission(this, permission) !=
                        PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    REQUEST_PERMISSIONS
                )
                return
            }
        }

        if (bluetoothAdapter.isEnabled) {
            startServer()
        } else {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_ENABLE_BT)
        }
    }

    // TODO 5: Implement the method that displays a dialog for selecting a paired device
    private fun listPairedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Lipsește permisiunea Bluetooth.", Toast.LENGTH_SHORT).show()
            return
        }

        val devices = bluetoothAdapter.bondedDevices.toList()

        if (devices.isEmpty()) {
            Toast.makeText(this, "Împerechează telefoanele din setările Bluetooth.", Toast.LENGTH_LONG).show()
            return
        }

        val labels = devices.map { device ->
            "${device.name ?: "Dispozitiv necunoscut"}\n${device.address}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Alege telefonul colegului")
            .setItems(labels) { _, index ->
                selectedDevice = devices[index]
                connectThread = ConnectThread(this, bluetoothAdapter, selectedDevice, MY_UUID)
                connectThread?.start()
            }
            .show()
    }

    // TODO 6: Implement server socket to listen for incoming connections
    private fun startServer() {
        acceptThread = AcceptThread(this, bluetoothAdapter, MY_UUID)
        acceptThread?.start()
    }

    // TODO 7: Implement the method that sends a message to the connected device
    private fun sendMessage() {
        sendButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isBlank()) return@setOnClickListener

            val connection = connectedThread
            if (connection == null) {
                Toast.makeText(this, "Conectează-te mai întâi la un telefon.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            connection.write(message.toByteArray(Charsets.UTF_8))
            addChatMessage("Me: $message")
            messageEditText.text.clear()
        }
    }

    // Update the UI with a new message
    fun addChatMessage(message: String) {
        chatMessages.add(message)
        chatArrayAdapter.notifyDataSetChanged()
    }

    // TODO 4: Handle permission results and activate Bluetooth programmatically
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            checkPermissions()
        } else {
            Toast.makeText(this, "Chatul are nevoie de permisiunile Bluetooth.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    fun manageConnectedSocket(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(this, socket).apply { start() }
    }

    // TODO 4: Handle Bluetooth enable result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_ENABLE_BT) return

        if (resultCode == RESULT_OK) {
            startServer()
        } else {
            Toast.makeText(this, "Activează Bluetooth pentru a folosi chatul.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // TODO 8: Clean up threads on activity destroy
    override fun onDestroy() {
        acceptThread?.cancel()
        connectThread?.cancel()
        connectedThread?.cancel()
        super.onDestroy()
    }
}
