/**
 *  Foscam
 *
 *  Copyright 2015 Shuai Wang
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition(name: "Foscam C1", namespace: "etrnls", author: "Shuai Wang") {
        capability "Actuator"
        capability "Sensor"
        capability "Image Capture"
        capability "Refresh"
 
        attribute "motion", "string"
 
        command "motionOn"
        command "motionOff"
    }

    preferences {
        input "username", "text", title: "Username", required: true
        input "password", "password", title: "Password", required: true
    }

    tiles {
        standardTile("image", "device.motion") {
            state "on", action: "motionOff", icon: "st.camera.dropcam-centered", backgroundColor: "#53a7c0"
            state "off", action: "motionOn", icon: "st.camera.dropcam-centered", backgroundColor: "#ffffff"
        }
        carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }
        standardTile("take", "device.image") {
            state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#ffffff", nextState: "taking"
            state "taking", label: "Taking", action: "", icon: "st.camera.dropcam", backgroundColor: "#53a7c0"
            state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#ffffff", nextState: "taking"
        }
        standardTile("refresh", "device.switch", decoration: "flat") {
            state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
        }

        main "image"
        details(["cameraDetails", "image", "take", "refresh"])
    }
}

def parse(String description) {
    def descMap = stringToMap(description)
    if (descMap.bucket && descMap.key) {
        putImageInS3(descMap.bucket, descMap.key)
    } else if (descMap.headers && descMap.body) {
        def data = new XmlSlurper().parseText(new String(descMap.body.decodeBase64()))
        def evt = parseMotionDetectConfig(data)
        if (evt) return evt
        log.warn "parse(data = '${data}') not handled"
    } else {
        log.warn "parse(descMap = '${descMap}') not handled"
    }
}

def putImageInS3(bucket, key) {
    log.trace "putImageInS3(${bucket}, ${key})"
    def s3ObjectContent
    try {
        def imageBytes = getS3Object(bucket, key + ".jpg")
        if (imageBytes) {
            s3ObjectContent = imageBytes.getObjectContent()
            def bytes = new ByteArrayInputStream(s3ObjectContent.bytes)
            storeImage(getPictureName(), bytes)
        }
    } catch(Exception e) {
        log.error e
    } finally {
        if (s3ObjectContent) { s3ObjectContent.close() }
    }
}

def parseMotionDetectConfig(config) {
    log.trace "parseMotionDetectConfig(${config})"
    if (config?.result != 0 || config.isEnable.isEmpty() || config.linkage.isEmpty() ||
        config.sensitivity.isEmpty() || config.triggerInterval.isEmpty() ||
        config.isMovAlarmEnable.isEmpty() || config.isPirAlarmEnable.isEmpty()) {
        return null
    } else if (config.isEnable == 1 && config.linkage == 0 &&
               config.sensitivity == 2 && config.triggerInterval == 0 &&
               config.isMovAlarmEnable == 1 && config.isPirAlarmEnable == 1 &&
               config.schedule0 == 281474976710655 &&
               config.schedule1 == 281474976710655 &&
               config.schedule2 == 281474976710655 &&
               config.schedule3 == 281474976710655 &&
               config.schedule4 == 281474976710655 &&
               config.schedule5 == 281474976710655 &&
               config.schedule6 == 281474976710655 &&
               config.area0 == 1023 && config.area1 == 1023 &&
               config.area2 == 1023 && config.area3 == 1023 &&
               config.area4 == 1023 && config.area5 == 1023 &&
               config.area6 == 1023 && config.area7 == 1023 &&
               config.area8 == 1023 && config.area9 == 1023) {
        return createEvent(name: "motion", value: "on")
    } else {
        return createEvent(name: "motion", value: "off")
    }
}

def take() {
    log.trace "take()"
    def hubAction = hubGet("snapPicture2", [])
    hubAction.options = [outputMsgToS3: true]
    hubAction
}

def refresh() {
    log.trace "refresh()"
    hubGet("getMotionDetectConfig", [])
}

def motionOn() {
    log.trace "motionOn()"
	sendEvent(name: "motion", value: "on")
    def params = [
        isEnable: 1,
        linkage: 0,
        sensitivity: 2,
        triggerInterval: 0,
        schedule0: 281474976710655,
        schedule1: 281474976710655,
        schedule2: 281474976710655,
        schedule3: 281474976710655,
        schedule4: 281474976710655,
        schedule5: 281474976710655,
        schedule6: 281474976710655,
        area0: 1023, area1: 1023,
        area2: 1023, area3: 1023,
        area4: 1023, area5: 1023,
        area6: 1023, area7: 1023,
        area8: 1023, area9: 1023,
    ]
    hubGet("setMotionDetectConfig", params)
}

def motionOff() {
    log.trace "motionOff()"
    sendEvent(name: "motion", value: "off")
    def params = [
        isEnable: 0,
    ]
    hubGet("setMotionDetectConfig", params)
}

private hubGet(cmd, params) {
    new physicalgraph.device.HubAction(
        method: "GET",
        path: "/cgi-bin/CGIProxy.fcgi",
        headers: [HOST: getHostAddress()],
        query: [cmd: cmd, usr: settings.username, pwd: settings.password] + params,
    )
}

private getPictureName() {
    def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
    return device.deviceNetworkId + "_${pictureUuid}.jpg"
}

private getHostAddress() {
    def parts = device.deviceNetworkId.split(":")
    def ip = [Integer.parseInt(parts[0][0..1], 16),
              Integer.parseInt(parts[0][2..3], 16),
              Integer.parseInt(parts[0][4..5], 16),
              Integer.parseInt(parts[0][6..7], 16)].join(".")
    def port = Integer.parseInt(parts[1], 16)
    return ip + ":" + port
}