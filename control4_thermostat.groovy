/**
 *  Control4 Thermostat
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
	definition(name: "Control4 Thermostat", namespace: "etrnls", author: "Shuai Wang") {
        capability "Actuator"
        capability "Sensor"
		capability "Thermostat"
        capability "Refresh"
        capability "Polling"
	}

	tiles {
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label: '${currentValue}°',
                backgroundColors: [
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
        standardTile("refresh", "device.temperature", decoration: "flat") {
			state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
		}
        standardTile("mode", "device.thermostatMode", decoration: "flat") {
			state "off", label: '${name}', action: "thermostat.setThermostatMode"
			state "heat", label: '${name}', action: "thermostat.setThermostatMode"
			state "cool", label: '${name}', action: "thermostat.setThermostatMode"
			state "auto heat", label: '${name}', action: "thermostat.setThermostatMode"
			state "auto cool", label: '${name}', action: "thermostat.setThermostatMode"
		}
        valueTile("operatingState", "device.thermostatOperatingState", decoration: "flat") {
            state "default", label: '${currentValue}'
		}
        valueTile("heatingSetpoint", "device.heatingSetpoint", decoration: "flat") {
            state "heat", label: '${currentValue}° heat'
		}
		valueTile("coolingSetpoint", "device.coolingSetpoint", decoration: "flat") {
			state "cool", label: '${currentValue}° cool'
		}
        main "temperature"
        details(["temperature", "refresh", "mode", "operatingState", "heatingSetpoint", "coolingSetpoint"])
	}
}

def parse(String description) {
    if (description?.startsWith("catchall:")) {
        def msg = zigbee.parse(description)
        def data = msg.clusterId == 0x0001 ? new String(msg.data as byte[]) : ""
        if (data.contains("sa c4.zt.tp")) {
            def temperature = parseTemperature(data.tokenize(" ")[3].trim())
            log.debug "parse temperature ${temperature}"
            return createEvent(name: "temperature", value: temperature)
        } else if (data.contains("sa c4.zt.sp")) {
            def coolingSetpoint = parseTemperature(data.tokenize(" ")[3].trim())
            def heatingSetpoint = parseTemperature(data.tokenize(" ")[4].trim())
            log.debug "parse coolingSetpoint ${coolingSetpoint} heatingSetpoint ${heatingSetpoint}"
            return [createEvent(name: "coolingSetpoint", value: coolingSetpoint),
                    createEvent(name: "heatingSetpoint", value: heatingSetpoint)]
        } else if (data.contains("sa c4.zt.mc")) {
            def mode = data.tokenize(" ")[3].trim()
            def modeMap = [
                "00": "off",
                "01": "heat",
                "02": "cool",
                "03": "auto heat",
                "04": "auto cool"]
            log.debug "parse mode ${modeMap[mode]}"
            return createEvent(name: "thermostatMode", value: modeMap[mode])
        } else if (data.contains("sa c4.zt.st")) {
            def state = data.tokenize(" ")[3].trim()
            def stateMap = [
                "00": "idle",
                "01": "fan only",
                "07": "cooling",
                "18": "heating"]
            log.debug "parse state ${stateMap[state]}"
            return createEvent(name: "thermostatOperatingState", value: stateMap[state])
        } else if (data.contains("c4:thermostat:ccz-t1-w")) {
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
	// TODO: handle 'thermostatMode' attribute
	// TODO: handle 'thermostatFanMode' attribute
	// TODO: handle 'thermostatOperatingState' attribute

}

// handle commands
def setHeatingSetpoint() {
	log.debug "Executing 'setHeatingSetpoint'"
	// TODO: handle 'setHeatingSetpoint' command
}

def setCoolingSetpoint() {
	log.debug "Executing 'setCoolingSetpoint'"
	// TODO: handle 'setCoolingSetpoint' command
}

def off() {
	log.debug "Executing 'off'"
	// TODO: handle 'off' command
}

def heat() {
	log.debug "Executing 'heat'"
	// TODO: handle 'heat' command
}

def emergencyHeat() {
	log.debug "Executing 'emergencyHeat'"
	// TODO: handle 'emergencyHeat' command
}

def cool() {
	log.debug "Executing 'cool'"
	// TODO: handle 'cool' command
}

def setThermostatMode() {
	log.debug "Executing 'setThermostatMode'"
	// TODO: handle 'setThermostatMode' command
}

def fanOn() {
	log.debug "Executing 'fanOn'"
	// TODO: handle 'fanOn' command
}

def fanAuto() {
	log.debug "Executing 'fanAuto'"
	// TODO: handle 'fanAuto' command
}

def fanCirculate() {
	log.debug "Executing 'fanCirculate'"
	// TODO: handle 'fanCirculate' command
}

def setThermostatFanMode() {
	log.debug "Executing 'setThermostatFanMode'"
	// TODO: handle 'setThermostatFanMode' command
}

def auto() {
	log.debug "Executing 'auto'"
	// TODO: handle 'auto' command
}

def refresh() {
    log.trace "refresh()"
    def scale = getTemperatureScale()
    log.debug "getTemperatureScale = ${scale}"
}

private parseTemperature(temperature) {
	log.trace "parseTemperature(${temperature})"
    def celsius = (Integer.parseInt(temperature, 16) - 0xAAA) / 0xA
    return getTemperatureScale() == "C" ? celsius : celsiusToFahrenheit(celsius)
}
