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
    }

    preferences {
        input "username", "text", title: "Username", required: true
        input "password", "password", title: "Password", required: true
    }

    tiles {
        standardTile("image", "device.image") {
            state "default", icon: "st.camera.dropcam-centered", backgroundColor: "#FFFFFF"
        }

		carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }

		standardTile("take", "device.image", width: 1, height: 1) {
            state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState: "taking"
            state "taking", label: "Taking", action: "", icon: "st.camera.dropcam", backgroundColor: "#53a7c0"
            state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState: "taking"
        }

        main "image"
        details(["cameraDetails", "take"])
    }
}

def parse(String description) {
	log.trace "parse(description = '${description}')"


	def map = stringToMap(description)
	log.debug map

	def result = []

	if (map.bucket && map.key)
	{
		putImageInS3(map)
	}
	else if (map.headers && map.body)
	{
		
	}

	result
}

def putImageInS3(map) {
log.trace "putImageInS3"

	def s3ObjectContent

	try {
		def imageBytes = getS3Object(map.bucket, map.key + ".jpg")

		if(imageBytes)
		{
			s3ObjectContent = imageBytes.getObjectContent()
			def bytes = new ByteArrayInputStream(s3ObjectContent.bytes)
			storeImage(getPictureName(), bytes)
            log.debug "storeImage done"
		}
	}
	catch(Exception e) {
		log.error e
	}
	finally {
		if (s3ObjectContent) { s3ObjectContent.close() }
	}
}

// handle commands
def take() {
	log.debug "Executing 'take'"
    
	def hubAction = new physicalgraph.device.HubAction(
		method: "GET",
		path: "/cgi-bin/CGIProxy.fcgi",
		headers: [HOST: getHostAddress()],
        query: [cmd: "snapPicture2", usr: settings.username, pwd: settings.password],
	)
	hubAction.options = [outputMsgToS3:true]
    log.debug hubAction
	hubAction
}

private getPictureName() {
	def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
	return device.deviceNetworkId + "_$pictureUuid" + ".jpg"
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
	def parts = device.deviceNetworkId.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}
