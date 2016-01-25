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

        fingerprint endpointId: "01", profileId: "0104", inClusters: "0003"
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
    def evt = zigbee.getEvent(description)
    if (evt) {
        log.debug "parse event ${evt}"
        return evt
    }

    if (description?.startsWith("catchall:")) {
        def msg = zigbee.parse(description)
        def data = msg.clusterId == 0x0001 ? new String(msg.data as byte[]) : ""
        if (data.contains("sa c4.dmx.cc 01 01")) {
            log.debug "parse switch on"
            evt = createEvent(name: "switch", value: "on")
        } else if (data.contains("sa c4.dmx.cc 05 01")) {
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
            def level = zigbee.convertHexToInt(data.tokenize(" ")[5])
            log.debug "parse level ${level}"
            if (level >= 0 && level <= 100) {
                evt = createEvent(name: "level", value: level)
            } else {
                log.error "parse level ${level} not within [0..100]"
            }
        } else if (data.contains("c4:control4_light:C4-APD120") ||
                   data.contains("c4:control4_light:C4-SW120277")) {
            // firmware?
        } else {
            if (data.length() > 0 && data.charAt(data.length() - 1) == '\n') {
                data = data.take(data.length() - 1)
            }
            log.warn "parse(data = '${data}' msg = '${msg}') not handled"
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
    zigbee.on()
}

def off() {
    log.trace "off()"
    zigbee.off()
}

def setLevel(value) {
    log.trace "setLevel(${value})"
    def cmds = []

    if (value == 0) {
        cmds << off()
    } else if (device.latestValue("switch") == "off") {
        cmds << on()
    }

    def level = Math.round(value * 255 / 100);
    cmds << zigbee.command(8, 4, zigbee.convertToHexString(level), "0000")

    cmds
}

def configure() {
    log.trace "configure()"
    zigbee.onOffConfig() + zigbee.levelConfig() + zigbee.onOffRefresh() + zigbee.levelRefresh()
}

def refresh() {
    log.trace "refresh()"
    zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.onOffConfig() + zigbee.levelConfig()
}

def poll() {
    log.trace "poll()"
    refresh()
}
