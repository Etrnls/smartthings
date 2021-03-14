metadata {
    definition(name: "Control4 Dimmer", namespace: "etrnls", author: "Shuai Wang") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        capability "Refresh"
        capability "Health Check"

        fingerprint endpointId: "01", profileId: "0104", inClusters: "0003"
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
    device.endpointId = 1
    // zigbee.on() = [st cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}, delay 2000]
    // 0x01: endpointId
    // 0x0006: zigbee On/Off cluster
    // 0x01: put the light in the on state
    zigbee.on()
}

def off() {
    log.trace "off()"
    device.endpointId = 1
    // zigbee.off() = [st cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}, delay 2000]
    // 0x01: endpointId
    // 0x0006: zigbee On/Off cluster
    // 0x00: put the light in the off state
    zigbee.off()
}

def setLevel(value) {
    log.trace "setLevel(${value})"
    device.endpointId = 1
    def cmds = []

    if (value == 0) {
        cmds << off()
    } else if (device.latestValue("switch") == "off") {
        cmds << on()
    }

    def level = Math.round(value * 255 / 100);
    // [st cmd 0x${device.deviceNetworkId} 0x01 0x0008 0x04 {<00~FF>0000}, delay 2000]
    // 0x0008: zigbee Level Control cluster
    // 0x04: set level
    // "0000" is transition time
    cmds << zigbee.command(0x0008, 0x04, zigbee.convertToHexString(level), "0000")

    cmds
}

def configure() {
    log.trace "configure()"
    device.endpointId = 1
    // zdo bind: zdo bind 0x${device.deviceNetworkId} <source endpoint> <dest endpoint> <cluster> <zigbee id>
    // onOffConfig() = [
    //     zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {},
    //     delay 2000,
    //     st cr 0x${device.deviceNetworkId} 0x01 0x0006 0x0000 0x10 0x0000 0x0258 {},
    //     delay 2000]
    // def onOffConfig(minReportTime=0, maxReportTime=600) {
    //     configureReporting(ONOFF_CLUSTER, 0x0000, DataType.BOOLEAN, minReportTime, maxReportTime, null)
    // }
    // levelConfig() = [
    //     zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {},
    //     delay 2000,
    //     st cr 0x${device.deviceNetworkId} 0x01 0x0008 0x0000 0x20 0x0001 0x0E10 {01},
    //     delay 2000]
    // def levelConfig(minReportTime=1, maxReportTime=3600, reportableChange=0x01) {
    //     configureReporting(LEVEL_CONTROL_CLUSTER, 0x0000, DataType.UINT8, minReportTime, maxReportTime, reportableChange)
    // }
    // onOffRefresh() = [st rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0000, delay 2000]
    // levelRefresh() = [st rattr 0x${device.deviceNetworkId} 0x01 0x0008 0x0000, delay 2000]
    // zigbee.configureReporting(Integer Cluster, Integer attributeId, Integer dataType,
    //                           Integer minReportTime, Integer MaxReportTime, [Integer reportableChange], Map additionalParams=[:])
    zigbee.onOffConfig() + zigbee.levelConfig() + zigbee.onOffRefresh() + zigbee.levelRefresh()
}

def refresh() {
    log.trace "refresh()"
    configure()
}

def ping() {
    log.trace "ping()"
    refresh()
}
