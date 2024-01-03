import java.util.concurrent.Semaphore

definition(
    name: "Hatch Rest/Restore Integration",
    namespace: "jnleonard3",
    author: "jnleonard3",
    description: "Control Hatch Rest/Restore (wifi-connected) devices",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "loginPage", title: "Hatch Login", nextPage: "devicesPage", uninstall: true) {
        section("Hatch Login") {
            input name: "hatchEmailAddress", type: "string", title: "Hatch Email Address", required: true
            input name: "hatchPassword", type: "string", title: "Hatch Password", required: true
        }
    }
    page(name: "devicesPage")
}

def installed() {
    log.debug "installed()"
    initialize()
}

def updated() {
    log.debug "updated()"
    initialize()
}

def uninstalled() {}

def initialize() {
    login()

    if (!settings.selectedDevices) return

    def childDeviceMap = [:]
    def childDevices = getChildDevices().each { value -> childDeviceMap[value.getDeviceNetworkId()] = value }

    settings.selectedDevices.each { mac ->
        if (childDeviceMap[mac]) {
            return;
        }

        addChildDevice(
            "jnleonard3",
            "Hatch Rest Plus Second-Gen",
            mac,
            [
                name: state.deviceData[mac].name
            ]
        )
    }
}

def getAwsIotClientCredentials() {
    login()

    if (!state.token) {
        return;
    }

    if (!state.awsCognitoParams) {
        httpGet(
            [
                uri: "https://data.hatchbaby.com/service/app/restPlus/token/v1/fetch",
                requestContentType: "application/json",
                headers:
                [
                    "X-HatchBaby-Auth": state.token
                ]
            ]
        )
        { resp ->
            state.awsCognitoParams = resp.getData().payload
        }
    }

    if (!state.mqttClientParams) {
        httpPostJson(
            [
                uri: "https://cognito-identity.${state.awsCognitoParams.region}.amazonaws.com",
                headers:
                [
                    "content-type": "application/x-amz-json-1.1",
                    "X-Amz-Target": "AWSCognitoIdentityService.GetCredentialsForIdentity",
                ],
                body: [
                    "IdentityId": state.awsCognitoParams.identityId,
                    "Logins":
                    [
                        "cognito-identity.amazonaws.com": state.awsCognitoParams.token,
                    ]
                ]
            ]
        )
        { resp ->
            log.debug resp.getData()
            state.mqttClientParams = resp.getData().Credentials
        }
    }

    return [
        host: state.awsCognitoParams.endpoint.replace('https://', ''),
        region: state.awsCognitoParams.region,
        accessKeyId: state.mqttClientParams.AccessKeyId,
        secretKey: state.mqttClientParams.SecretKey,
        sessionToken: state.mqttClientParams.SessionToken,
    ]
}

def login() {
    if (state.token) {
        return;
    }

    try {
        httpPostJson(
            [
                uri: "https://data.hatchbaby.com/public/v1/login",
                body:
                [
                    email: settings.hatchEmailAddress,
                    password: settings.hatchPassword
                ]
            ]
        )
        { resp ->
            if (resp.getData().token) {
                state.token = resp.getData().token
            }
            state.loginLock.release()
        }
    } catch(Exception ex) {
        // Do nothing
    }
}

def devicesPage() {
    def deviceLabels = [:]

    login()

    if (state.token) {
        httpGet(
            [
                uri: "https://data.hatchbaby.com/service/app/iotDevice/v2/fetch?iotProducts=restPlus&iotProducts=restMini&iotProducts=riotPlus",
                requestContentType: "application/json",
                headers:
                [
                    "X-HatchBaby-Auth": state.token
                ]
            ]
        )
        { resp ->
            log.debug resp.getData()
            resp.getData().payload.each {data ->
                deviceLabels[data.macAddress] = data.name

                if (!state.deviceData) {
                    state.deviceData = [:]
                }

                state.deviceData[data.macAddress] = data
            }
        }
    }

    dynamicPage(
        name: "devicesPage",
        title: "Hatch Devices",
        install: true,
        uninstall: true
    ) {
        section('<h2>Device Discovery</h2>') {
            input(
                'selectedDevices',
                'enum',
                required: false,
                title: "Select Hatch Devices",
                multiple: true,
                options: deviceLabels
            )
        }
    }
}