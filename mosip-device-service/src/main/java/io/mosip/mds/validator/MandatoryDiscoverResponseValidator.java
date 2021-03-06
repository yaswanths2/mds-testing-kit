package io.mosip.mds.validator;

import java.util.ArrayList;
import java.util.List;

import io.mosip.mds.dto.DiscoverResponse;
import io.mosip.mds.dto.ValidateResponseRequestDto;
import io.mosip.mds.entitiy.Validator;

public class MandatoryDiscoverResponseValidator  extends Validator {

	@Override
	protected List<String> DoValidate(ValidateResponseRequestDto response) {

		List<String> errors = new ArrayList<>();
		DiscoverResponse discoverResponse = response.discoverResponse;

		// Check for callbackId block
		if(discoverResponse.callbackId == null || discoverResponse.callbackId.isEmpty())
		{
			errors.add("DeviceInfo response does not contain callbackId");
			return errors;
		}

		// Check for certification block
		if(discoverResponse.certification == null || discoverResponse.certification.isEmpty())
		{
			errors.add("DeviceInfo response does not contain certification");
			return errors;
		}

		// Check for deviceCode block
		if(discoverResponse.deviceCode == null || discoverResponse.deviceCode.isEmpty())
		{
			errors.add("DeviceInfo response does not contain deviceCode");
			return errors;
		}
		// Check for deviceId block
		if(discoverResponse.deviceId == null)
		{
			errors.add("DeviceInfo response does not contain deviceId");
			return errors;
		}

		// Check for deviceStatus block
		if(discoverResponse.deviceStatus == null || discoverResponse.deviceStatus.isEmpty())
		{
			errors.add("DeviceInfo response does not contain deviceStatus");
			return errors;
		}

		// TODO Check for deviceSubId block
		if(discoverResponse.deviceSubId == null || discoverResponse.deviceSubId.length == 0)
		{
			errors.add("DeviceInfo response does not contain deviceSubId");
			return errors;
		}

		// Check for digitalId block
		if(discoverResponse.digitalId == null || discoverResponse.digitalId.isEmpty())
		{
			errors.add("DeviceInfo response does not contain digitalId");
			return errors;
		}

		// Check for purpose block
		if(discoverResponse.purpose == null || discoverResponse.purpose.isEmpty())
		{
			errors.add("DeviceInfo response does not contain purpose");
			return errors;
		}

		// TODO Check for specVersion block
		if(discoverResponse.specVersion == null || discoverResponse.specVersion.length == 0)
		{
			errors.add("DeviceInfo response does not contain specVersion");
			return errors;
		}

		return errors;

	}

}
