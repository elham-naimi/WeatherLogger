package com.github.nekdenis.weatherlogger.messaging.client

import com.github.nekdenis.weatherlogger.core.system.Logger
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

private const val TAG = "MQTTCLIENT::"

interface MqttClient {
    fun connect(
            serverUrl: String,
            clientName: String,
            onConnected: () -> Unit,
            onMessage: (topic: String, message: String) -> Unit,
            onError: (e: Throwable) -> Unit
    )

    fun subscribeToTopic(subscriptionTopic: String, messageListener: MessageListener)
    fun subscribeToTopic(subscriptionTopic: String)
    fun publishMessage(publishMessage: String, publishTopic: String)
    fun disconnect()
}

interface MessageListener {
    fun onReceived(topic: String, message: String)
}

class MqttClientImpl(val logger: Logger) : com.github.nekdenis.weatherlogger.messaging.client.MqttClient {

    lateinit var mqttAndroidClient: MqttClient

    var qos = 2
    var persistence = MemoryPersistence()

    override fun connect(
            serverUrl: String,
            clientName: String,
            onConnected: () -> Unit,
            onMessage: (topic: String, message: String) -> Unit,
            onError: (e: Throwable) -> Unit
    ) {
        try {
            mqttAndroidClient = MqttClient(serverUrl, clientName, persistence)
            val connOpts = MqttConnectOptions()
            connOpts.isCleanSession = true
            logger.d("$TAG Connecting to broker: " + serverUrl)
            mqttAndroidClient.connect(connOpts)
            mqttAndroidClient.setCallback(object : MqttCallback {
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    logger.e(message = "$TAG messageArrived but not processed by special callback! Received text: $topic : $message")
                    onMessage(topic ?: "null", message?.let { String(it.payload) } ?: "")
                }

                override fun connectionLost(cause: Throwable?) {
                    logger.e(message = "$TAG connectionLost()")
                    onError(cause ?: Exception("Mqqt connection lost"))
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    logger.d(message = "$TAG deliveryComplete()")
                }

            })
            logger.d("$TAG Connected")
            onConnected()
        } catch (e: MqttException) {
            onError(e)
            logger.e(e, "$TAG can't connect:  ${e.message}")
        }
    }

    override fun subscribeToTopic(subscriptionTopic: String) {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, qos)
        } catch (ex: MqttException) {
            logger.e(ex, "$TAG Exception whilst subscribing")
        }
    }

    override fun subscribeToTopic(subscriptionTopic: String, messageListener: MessageListener) {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, qos) { topic, message ->
                val messageString = String(message.payload)
                logger.d(message = "$TAG Received text: $topic : $messageString")
                messageListener.onReceived(topic = topic, message = messageString)
            }
        } catch (ex: MqttException) {
            logger.e(ex, "$TAG Exception whilst subscribing")
        }
    }

    override fun publishMessage(publishMessage: String, publishTopic: String) {
        try {
            logger.d("$TAG Publishing text: " + publishMessage)
            val message = MqttMessage(publishMessage.toByteArray())
            message.qos = qos
            mqttAndroidClient.publish(publishTopic, message)
            logger.d("$TAG Message Published")
            if (!mqttAndroidClient.isConnected) {
                logger.e(message = "$TAG not connected while publishing")
            }
        } catch (e: MqttException) {
            logger.e(e, "$TAG Error Publishing:  ${e.message}")
        }
    }

    override fun disconnect() {
        try {
            mqttAndroidClient.disconnect()
            logger.d("$TAG Disconnected")
        } catch (e: MqttException) {
            logger.e(e, "$TAG can't disconnect connect:  ${e.message}")
        }
    }
}