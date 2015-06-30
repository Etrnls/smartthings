/**
 *  Control4 Remote
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
    definition (name: "Control4 Remote", namespace: "etrnls", author: "Shuai Wang") {
        capability "Actuator"
        capability "Sensor"
        capability "Button"
    }

    tiles {
        standardTile("button", "device.button", width: 2, height: 2) {
            state "default", icon: "st.unknown.zwave.remote-controller"
        }
        main "button"
        details(["button"])
    }
}

def parse(String description) {
    if (description?.startsWith("catchall:")) {
        def msg = zigbee.parse(description)
        def data = msg.clusterId == 0x0001 ? new String(msg.data as byte[]) : ""
        if (data.contains("sa c4.zr.mot")) {
            // motion
        } else if (data.contains("sa c4.zr.bb")) {
            def button = Integer.parseInt(data.tokenize(" ")[3], 16)
            log.debug "parse button ${button} down"
            return createEvent(name: "button", value: "down", data: [buttonNumber: button])
        } else if (data.contains("sa c4.zr.be")) {
            def button = Integer.parseInt(data.tokenize(" ")[3], 16)
            log.debug "parse button ${button} up"
            return createEvent(name: "button", value: "up", data: [buttonNumber: button])
        } else if (data.contains("sa c4.zr.bh")) {
            def button = Integer.parseInt(data.tokenize(" ")[3], 16)
            log.debug "parse button ${button} hold"
            return createEvent(name: "button", value: "hold", data: [buttonNumber: button])
        } else if (data.contains("c4:control4_sr250b:C4-SR250B")) {
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
}
