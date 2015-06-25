/**
 *  Control4 Dimmer
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
    definition(name: "Control4 Dimmer", namespace: "etrnls", author: "Shuai Wang") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        capability "Refresh"
        capability "Polling"
    }

    tiles {
        standardTile("switch", "device.switch", width: 2, height: 2) {
            state "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#79b821"
            state "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff"
        }
        standardTile("refresh", "device.switch", decoration: "flat") {
            state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
        }
        controlTile("levelControl", "device.level", "slider", width: 3, range: "(0..100)") {
            state "level", action: "switch level.setLevel"
        }
        valueTile("levelValue", "device.level", decoration: "flat") {
            state "level", label: '${currentValue} %', unit: "%"
        }

        main "switch"
        details(["switch", "refresh", "levelControl", "levelValue"])
    }
}

def parse(String description) {
    def evt = null
    if (description?.startsWith("catchall:")) {
        def msg = zigbee.parse(description)
        def data = msg.clusterId == 0x0001 ? new String(msg.data as byte[]) : ""
        if ((msg.clusterId == 0x0006 && msg.data == [0, 0, 0, 16, 1]) ||
            data.contains("sa c4.dmx.cc 01 01")) {
            log.debug "parse switch on"
            evt = createEvent(name: "switch", value: "on")
        } else if ((msg.clusterId == 0x0006 && msg.data == [0, 0, 0, 16, 0]) ||
                   data.contains("sa c4.dmx.cc 05 01")) {
            log.debug "parse switch off"
            evt = createEvent(name: "switch", value: "off")
        } else if (data.contains("sa c4.dmx.bp") || data.contains("sa c4.dmx.sc") ||
                   data.contains("sa c4.dmx.hc") || data.contains("sa c4.dmx.he") ||
                   data.contains("sa c4.dmx.cc")) {
            // bp - button down
            // sc - button up
            // hc - button hold
            // he - button hold end
            // cc - button click
            // params:
            // 01 top button
            // 05 bottom button
            // bp 01 - top button down
            // cc 05 02 - bottom button click twice
        } else if (data.contains("sa c4.dmx.ls")) {
            def level = Integer.parseInt(data.tokenize(" ")[5], 16)
            log.debug "parse level ${level}"
            if (level >= 0 && level <= 100) {
                evt = createEvent(name: "level", value: level)
            } else {
                log.error "parse level ${level} not within [0..100]"
            }
        } else if (data.contains("c4:control4_light:C4-APD120")) {
            // firmware?
        } else {
            if (data.length() > 0 && data.charAt(data.length() - 1) == '\n') {
                data = data.take(data.length() - 1)
            }
            log.warn "parse(data = '${data}' msg = '${msg}') not handled"
        }
    } else if (description?.startsWith("read attr -")) {
        def descMap = stringToMap(description - "read attr - ")
        if (descMap.cluster == "0008" && descMap.attrId == "0000") {
            def level = Math.round(Integer.parseInt(descMap.value, 16) * 100 / 255)
            log.debug "parse attr level ${level}"
            if (level >= 0 && level <= 100) {
                evt = createEvent(name: "level", value: level)
            } else {
                log.error "parse attr level ${level} not within [0..100]"
            }
        } else {
            log.warn "parse(read attr descMap = '${descMap}') not handled"
        }
    } else {
        log.warn "parse(description = '${description}') not handled"
    }
    if (evt?.name == "level") {
        if (evt.value == 0) {
            return [evt, createEvent(name: "switch", value: "off")]
        } else if (device.latestValue("switch") == "off") {
            return [evt, createEvent(name: "switch", value: "on")]
        }
    }
    evt
}

def on() {
    log.trace "on()"
    sendEvent(name: "switch", value: "on")
    "st cmd 0x${device.deviceNetworkId} 1 6 1 {}"
}

def off() {
    log.trace "off()"
    sendEvent(name: "switch", value: "off")
    "st cmd 0x${device.deviceNetworkId} 1 6 0 {}"
}

def setLevel(value) {
    log.trace "setLevel(${value})"
    def cmds = []

    if (value == 0) {
        cmds << off()
    } else if (device.latestValue("switch") == "off") {
        cmds << on()
    }

    sendEvent(name: "level", value: value)
    def level = String.format("%02x", Math.round(value * 255 / 100));
    cmds << "st cmd 0x${device.deviceNetworkId} 1 8 4 {${level} 0000}"

    cmds
}

def refresh() {
    log.trace "refresh()"
    [
        "st rattr 0x${device.deviceNetworkId} 1 6 0", "delay 200",
        "st rattr 0x${device.deviceNetworkId} 1 8 0", "delay 200",
    ]
}

def poll() {
    log.trace "poll()"
    refresh()
}