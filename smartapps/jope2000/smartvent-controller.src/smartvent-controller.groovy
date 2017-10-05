/**
 *  SmartVent Controller
 *
 *  Copyright 2017 J&ouml;rg Penndorf
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
definition(
    name: "SmartVent Controller",
    namespace: "jope2000",
    author: "J&ouml;rg Penndorf",
    description: "nothing yet",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

def getNumberOfRooms()
{
	return 3
}

preferences {
	section("Thermostat") {
		input (name:"thermostat", type: "capability.thermostat", title: "Main thermostat?",required:true)
	}
    
    for (int roomNumber = 1; roomNumber <= getNumberOfRooms(); roomNumber++)
    {
        section("Rooom ${roomNumber}") {
            input (name:"room${roomNumber}_tempSensor", type: "capability.temperatureMeasurement", title: "Temperature Sensor?",required:true)
            input (name:"room${roomNumber}_desiredTemp", type:"decimal", title: "Temp Threshold [${getTemperatureScale()}]", required: true)	
            input (name:"room${roomNumber}_vents", type: "capability.switchLevel", title: "Vents?",required:true, multiple:true)
            input (name:"room${roomNumber}_minVentLevel", type: "decimal", title: "Minimum Vent Level", required:true)
            input (name:"room${roomNumber}_maxVentLevel", type: "decimal", title: "Maximum Vent Level", required:true)
        }
    }
}

def installed() 
{
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() 
{
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() 
{
	
    if(thermostat)
    {
    	subscribe(thermostat, "thermostatOperatingState", thermostatOperatingHandler)
    }
    
    for (int roomNumber = 1; roomNumber <= getNumberOfRooms(); roomNumber++)
    {
    	def tempSensor = settings["room${roomNumber}_tempSensor"]
        subscribe(tempSensor, "temperature", temperatureSensorChangeHandler)
    }
}

def thermostatOperatingHandler(evt) 
{
	log.debug "Thermostat Operating now: $evt.value"
    for (int roomNumber = 1; roomNumber <= getNumberOfRooms(); roomNumber++)
    {
    	adjustVentSettingsInRoom(roomNumber)
    }
}

def temperatureSensorChangeHandler(evt) 
{
	def sensor = evt.getDevice()
 
 	log.debug "Thermostat Operating State: ${thermostat.currentThermostatOperatingState}"
 	log.debug "Thermostat Heating Setpoint: ${thermostat.currentHeatingSetpoint.toDouble()}"
    log.debug "Thermostat Cooling Setpoint: ${thermostat.currentCoolingSetpoint.toDouble()}"
    
    log.debug "Thermostat is Heating: ${isHeatingMode()}"
 	log.debug "Thermostat is Cooling: ${isCoolingMode()}"
 
    if(sensor == null)
    {
    	log.debug "Unable to determine device that send the Event!"
    }
    else
    {
    	def sensorName = sensor.getDisplayName()
    	log.debug "Event received from $sensorName"
        
        def roomNumber = getRoomNumberOfTemperatureSensor(sensor)
        if(roomNumber != null)
        {
        	log.debug "sensor is in room $roomNumber"
            adjustVentSettingsInRoom(roomNumber)
        }
        else
        {
        	log.debug "FAILED to determine room number!"
        }
    }
}

def getRoomNumberOfTemperatureSensor(temperatureSensor)
{
	if(temperatureSensor == null)
    {
		return null
    }
    
    for (int roomNumber = 1; roomNumber <= getNumberOfRooms(); roomNumber++)
    {
    	if(settings["room${roomNumber}_tempSensor"]?.id == temperatureSensor.id)
        {
        	return roomNumber
        }
    }

	return null
}

def adjustVentSettingsInRoom(roomNumber)
{
	if(roomNumber == null)
    {
    	log.debug "no room number specified!"
    	return
    }
	
    def temperatureSensor = settings["room${roomNumber}_tempSensor"]
    def vents = settings["room${roomNumber}_vents"]
    def desiredRoomTemperature = settings["room${roomNumber}_desiredTemp"]
    
    if(temperatureSensor == null)
    {
    	log.debug "no temperature sensor defined for room ${roomNumber}"
        return
    }
    
    if(vents == null || vents.size() == 0)
    {
    	log.debug "no vents defined for room ${roomNumber}"
        return
    }
    
	def temperatureSensorTemperature = temperatureSensor.currentTemperature.toFloat().round(1)
	log.debug "Room ${roomNumber} Temp Sensor: $temperatureSensorTemperature"
    
    if(temperatureSensorTemperature == desiredRoomTemperature)
    {
        if(vents)
        {
        	log.debug "Temperature reached. Closing vents in room ${roomNumber}"
            vents.setLevel(settings["room${roomNumber}_minVentLevel"])
        }
        
        return
    }
    else if(temperatureSensorTemperature < desiredRoomTemperature)
    {
    	// temperature too low, heating is needed
        if(isHeatingMode() && vents != null)
        {
        	log.debug "Room ${roomNumber} is too cool and heating is active. Opening vents"
            vents.setLevel(settings["room${roomNumber}_maxVentLevel"])
        }
        
        if(isFanCirculationModeEnabled() && thermostat.currentTemperature > temperatureSensorTemperature)
        {
        	log.debug "Room ${roomNumber} is too cool. Use fan circulation to heat room"
            vents.setLevel(settings["room${roomNumber}_maxVentLevel"])
        }
    }
    else if(temperatureSensorTemperature > desiredRoomTemperature)
    {
    	// temperature too high, cooling is needed
        if(isCoolingMode() && vents != null)
        {
        	log.debug "Room ${roomNumber} is too hot and cooling is active. Opening vents"
            vents.setLevel(settings["room${roomNumber}_maxVentLevel"])
        }
        
        if(isFanCirculationModeEnabled() && thermostat.currentTemperature < temperatureSensorTemperature)
        {
        	log.debug "Room ${roomNumber} is too hot. Use fan circulation to cool room"
            vents.setLevel(settings["room${roomNumber}_maxVentLevel"])
        }
    }
    
    def thermostatTemperature = thermostat.currentTemperature.toFloat().round(1)
    log.debug "thermostat temp: $thermostatTemperature"
}

def isHeatingMode()
{
	if(thermostat)
    {
    	def operationState = thermostat.currentThermostatOperatingState
        
        // thermostat is actively heating
        if(operationState == "heating")
        {
        	return true
        }
        
        // thermostat is not actively heating
        if(operatingState == "idle")
        {
            def thermostatCoolingSetpoint = thermostat.currentCoolingSetpoint.toDouble()
            def thermostatTemperature = thermostat.currentTemperature.toDouble()
            
            if(thermostatTemperature < thermostatCoolingSetpoint)
            {
            	return true
            }
        }
    }

	return false
}

def isCoolingMode()
{
    def operationState = thermostat.currentThermostatOperatingState
    
    // thermostat is actively cooling
    if(operationState == "cooling")
    {
        return true
    }
    
    // thermostat is not actively cooling
    if(operatingState == "idle")
    {
        def thermostatHeatingSetpoint = thermostat.currentHeatingSetpoint.toDouble()
        def thermostatTemperature = thermostat.currentTemperature.toDouble()

        if(thermostatTemperature > thermostatHeatingSetpoint)
        {
            return true
        }  
    }
    
	return false
}

def isFanCirculationModeEnabled()
{
	def fanMode = thermostat.currentThermostatFanMode
    return fanMode != null && (fanMode == "on" || fanMode == "auto")
}