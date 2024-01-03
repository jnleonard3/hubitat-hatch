metadata {
   definition (name: "Hatch Rest Plus Second-Gen", namespace: "jnleonard3", author: "jnleonard3") {
      capability "Actuator"
      capability "Switch"
      capability "Initialize"
   }

   preferences {
      // none in this driver
   }
}

def installed() {
   log.debug "installed()"
   initialize()
}

def updated() {
   log.debug "updated()"
   initialize()
}


def subscribeToMqtt() {
    def params = getParent().getAwsCognitoParameters()

    interfaces.mqtt.connect("tcp://test.mosquitto.org:1883", "mqtttest123", null, null)
}

def initialize() {
   log.debug "initialize()"

   def credentials = getParent().getAwsIotClientCredentials()

   log.debug credentials

   def today = new SimpleDateFormat("YYYYMMDD").format(new Timestamp(new Date().getTime()))

//    interfaces.mqtt.connect("tcp://test.mosquitto.org:1883", "mqtttest123", null, null)

    def dateMac = javax.crypto.Mac.getInstance("HmacSHA256")
    dateMac.init(new SecretKeySpec("AWS4${credentials.secretKey}".getBytes(), HMAC_SHA_256))
    def date = dateMac.update()


    interfaces.webSocket.connect(
        "wss://${credentials.host}/mqtt",
        headers: [
            "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
            "X-Amz-Credential": "${credentials.accessKeyId}/${today}/${credentials.region}/iotdevicegateway/aws4_request",
            "X-Amz-Date": now,
            "X-Amz-SignedHeaders": "host",
            "X-Amz-Signature": "",
        ]
    )
}

def on() {

}

def off() {

}