metadata {
	definition(name: "Etherdyne Switch", namespace: "Etherdyne", author: "i3 Productions", ocfDeviceType: "oic.d.switch", runLocally: true, minHubCoreVersion: '000.019.00012', executeCommandsLocally: false, genericHandler: "Z-Wave") {
		capability "Switch"
		capability "Refresh"
		capability "Actuator"
        capability "Polling"
		capability "Sensor"
		capability "Health Check"
        
        attribute "power", "number"
        attribute "current", "number"
        attribute "voltage", "number"
        attribute "pwm", "number"
        
        command "configurationGet"
        command "restart"
        command "setPWM"

        fingerprint mfr: "0000", prod: "0004", model: "0002", deviceJoinName: "Etherdyne Switch"
	}

	tiles(scale: 2) {
    	multiAttributeTile(name:"tile", type:"generic", width:6, height:4) {
        	tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff"
            }
        }
        valueTile("powerTile", "device.power", width: 2, height: 2) {
        	state "val", label:'Power:\n${currentValue} mW', defaultState: true
        }
        valueTile("currentTile", "device.current", width: 2, height: 2) {
        	state "val", label:'Current:\n${currentValue} mA', defaultState: true
        }
        valueTile("voltageTile", "device.voltage", width: 2, height: 2) {
        	state "val", label:'Voltage:\n${currentValue} mV', defaultState: true
        }
        valueTile("pwmValue", "device.pwm", width: 2, height: 2) {
        	state "val", label:'PWM: ${currentValue}%'
        }
        controlTile("pwm", "device.pwm", "slider", height: 2, width: 2, inactiveLabel: false) {
    		state "pwm", label:"setpwm", action:"setPWM"
		}
        standardTile("restart", "device.restart", width: 2, height: 2) {
        	state "val", action:"restart", icon:"st.secondary.refresh"
        }
		main "tile"
		details(["tile", "powerTile", "currentTile", "voltageTile", "pwmValue", "pwm", "restart"])
	}
}

def installed() {
	// Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    hubConfigurationGet()
    state.lastUpdate = new Date().time
}

def updated() {
	response(refresh())
}

def parse(description) {
	def result = null
	if (description.startsWith("Err 106")) {
		result = createEvent(descriptionText: description, isStateChange: true)
	} else if (description != "updated") {
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x70: 1, 0x98: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
			log.debug("'$description' parsed to $result")
		} else {
			log.debug("Couldn't zwave.parse '$description'")
		}
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	createEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1])
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	switch (cmd.parameterNumber) {
    	case 1:
        	pause(5000)
            state.lastUpdate = new Date().time
            hubConfigurationGet()
        	return createEvent(name: "power", value: cmd.scaledConfigurationValue)
        case 2:
        	return createEvent(name: "current", value: cmd.scaledConfigurationValue)
        case 3:
        	return createEvent(name: "voltage", value: cmd.scaledConfigurationValue)
        case 5:
        	return createEvent(name: "pwm", value: cmd.scaledConfigurationValue/100)
        default:
        	log.debug "Parameter Number: $cmd.parameterNumber"
        	null
    }
}

def pause(millis) {
   def passed = 0
   def now = new Date().time
   while ( passed < millis ) {
       passed = new Date().time - now
   }
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Unhandled: $cmd"
	null
}

def on() {
	commands([
		zwave.basicV1.basicSet(value: 0xFF),
		zwave.basicV1.basicGet()
	])
}

def off() {
	commands([
		zwave.basicV1.basicSet(value: 0x00),
		zwave.basicV1.basicGet()
	])
}

def setPWM(value) {
    sendEvent(name: "pwm", value: value)
    command(zwave.configurationV2.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: value*100))
}

def configurationGet() {
	commands([
    	zwave.configurationV2.configurationGet(parameterNumber: 1),
    	zwave.configurationV2.configurationGet(parameterNumber: 2),
    	zwave.configurationV2.configurationGet(parameterNumber: 3),
    	zwave.configurationV2.configurationGet(parameterNumber: 5)
    ])
}

def hubConfigurationGet() {
	sendHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 1).format())
	sendHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 2).format())
	sendHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 3).format())
	sendHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 5).format())
}

def restart() {
	def diff = new Date().time - state.lastUpdate
	if (diff > 20 * 1000) {
        hubConfigurationGet()
    } else {
    	log.error "Its only been $diff ms since the last update, please wait 20 sec"
    }
}

def ping() {
	refresh()
}

def poll() {
	refresh()
}

def refresh() {
	command(zwave.basicV1.basicGet())
}

private command(physicalgraph.zwave.Command cmd) {
	if ((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay = 200) {
	delayBetween(commands.collect { command(it) }, delay)
}
