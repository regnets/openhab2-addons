/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.type;

import static org.openhab.binding.homematic.HomematicBindingConstants.*;
import static org.openhab.binding.homematic.internal.misc.HomematicConstants.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterBuilder;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterGroup;
import org.eclipse.smarthome.config.core.ParameterOption;
import org.eclipse.smarthome.core.thing.DefaultSystemChannelTypeProvider;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ChannelDefinition;
import org.eclipse.smarthome.core.thing.type.ChannelGroupDefinition;
import org.eclipse.smarthome.core.thing.type.ChannelGroupType;
import org.eclipse.smarthome.core.thing.type.ChannelGroupTypeUID;
import org.eclipse.smarthome.core.thing.type.ChannelType;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.types.StateDescription;
import org.eclipse.smarthome.core.types.StateOption;
import org.openhab.binding.homematic.internal.model.HmChannel;
import org.openhab.binding.homematic.internal.model.HmDatapoint;
import org.openhab.binding.homematic.internal.model.HmDevice;
import org.openhab.binding.homematic.internal.model.HmParamsetType;
import org.openhab.binding.homematic.type.MetadataUtils.OptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates ThingTypes based on metadata from a Homematic gateway.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class HomematicTypeGeneratorImpl implements HomematicTypeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(HomematicTypeGeneratorImpl.class);
    private static URI configDescriptionUriChannel;

    private HomematicThingTypeProvider thingTypeProvider;
    private HomematicChannelTypeProvider channelTypeProvider;
    private HomematicConfigDescriptionProvider configDescriptionProvider;
    private Map<String, Set<String>> firmwaresByType = new HashMap<String, Set<String>>();

    private static final String[] STATUS_DATAPOINT_NAMES = new String[] { DATAPOINT_NAME_UNREACH,
            DATAPOINT_NAME_CONFIG_PENDING, DATAPOINT_NAME_DEVICE_IN_BOOTLOADER, DATAPOINT_NAME_UPDATE_PENDING };

    private static final String[] IGNORE_DATAPOINT_NAMES = new String[] { VIRTUAL_DATAPOINT_NAME_BATTERY_TYPE,
            VIRTUAL_DATAPOINT_NAME_FIRMWARE, VIRTUAL_DATAPOINT_NAME_RELOAD_FROM_GATEWAY, DATAPOINT_NAME_AES_KEY };

    public HomematicTypeGeneratorImpl() {
        try {
            configDescriptionUriChannel = new URI(CONFIG_DESCRIPTION_URI_CHANNEL);
        } catch (Exception ex) {
            logger.warn("Can't create ConfigDescription URI '{}', ConfigDescription for channels not avilable!",
                    CONFIG_DESCRIPTION_URI_CHANNEL);
        }
    }

    protected void setThingTypeProvider(HomematicThingTypeProvider thingTypeProvider) {
        this.thingTypeProvider = thingTypeProvider;
    }

    protected void unsetThingTypeProvider(HomematicThingTypeProvider thingTypeProvider) {
        this.thingTypeProvider = null;
    }

    protected void setChannelTypeProvider(HomematicChannelTypeProvider channelTypeProvider) {
        this.channelTypeProvider = channelTypeProvider;
    }

    protected void unsetChannelTypeProvider(HomematicChannelTypeProvider channelTypeProvider) {
        this.channelTypeProvider = null;
    }

    protected void setConfigDescriptionProvider(HomematicConfigDescriptionProvider configDescriptionProvider) {
        this.configDescriptionProvider = configDescriptionProvider;
    }

    protected void unsetConfigDescriptionProvider(HomematicConfigDescriptionProvider configDescriptionProvider) {
        this.configDescriptionProvider = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generate(HmDevice device) {
        ThingTypeUID thingTypeUID = UidUtils.generateThingTypeUID(device);
        ThingType tt = thingTypeProvider.getThingType(thingTypeUID, Locale.getDefault());
        if (tt == null || device.isGatewayExtras()) {
            logger.debug("Generating ThingType for device '{}' with {} datapoints", device.getType(),
                    device.getDatapointCount());

            List<ChannelGroupType> groupTypes = new ArrayList<ChannelGroupType>();
            for (HmChannel channel : device.getChannels()) {
                List<ChannelDefinition> channelDefinitions = new ArrayList<ChannelDefinition>();
                // generate channel
                for (HmDatapoint dp : channel.getDatapoints().values()) {
                    if (!isStatusDatapoint(dp) && !isIgnoredDatapoint(dp)) {
                        if (dp.getParamsetType() == HmParamsetType.VALUES) {
                            ChannelTypeUID channelTypeUID = UidUtils.generateChannelTypeUID(dp);
                            ChannelType channelType = channelTypeProvider.getChannelType(channelTypeUID,
                                    Locale.getDefault());
                            if (channelType == null) {
                                channelType = createChannelType(dp, channelTypeUID);
                                channelTypeProvider.addChannelType(channelType);
                            }

                            ChannelDefinition channelDef = new ChannelDefinition(dp.getName(), channelType.getUID());
                            channelDefinitions.add(channelDef);
                        }
                    }
                }

                // generate group
                ChannelGroupTypeUID groupTypeUID = UidUtils.generateChannelGroupTypeUID(channel);
                ChannelGroupType groupType = channelTypeProvider.getChannelGroupType(groupTypeUID, Locale.getDefault());
                if (groupType == null) {
                    String groupLabel = String.format("%s",
                            WordUtils.capitalizeFully(StringUtils.replace(channel.getType(), "_", " ")));
                    groupType = new ChannelGroupType(groupTypeUID, false, groupLabel, null, channelDefinitions);
                    channelTypeProvider.addChannelGroupType(groupType);
                    groupTypes.add(groupType);
                }

            }
            tt = createThingType(device, groupTypes);
            thingTypeProvider.addThingType(tt);
        }
        addFirmware(device);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFirmwares() {
        for (String deviceType : firmwaresByType.keySet()) {
            Set<String> firmwares = firmwaresByType.get(deviceType);
            if (firmwares.size() > 1) {
                logger.info(
                        "Multiple firmware versions for device type '{}' found ({}). "
                                + "Make sure, all devices of the same type have the same firmware version, "
                                + "otherwise you MAY have channel and/or datapoint errors in the logfile",
                        deviceType, StringUtils.join(firmwares, ", "));
            }
        }
    }

    /**
     * Adds the firmware version for validation.
     */
    private void addFirmware(HmDevice device) {
        if (!StringUtils.equals(device.getFirmware(), "?") && !DEVICE_TYPE_VIRTUAL.equals(device.getType())
                && !DEVICE_TYPE_VIRTUAL_WIRED.equals(device.getType())) {
            Set<String> firmwares = firmwaresByType.get(device.getType());
            if (firmwares == null) {
                firmwares = new HashSet<String>();
                firmwaresByType.put(device.getType(), firmwares);
            }
            firmwares.add(device.getFirmware());
        }
    }

    /**
     * Creates the ThingType for the given device.
     */
    private ThingType createThingType(HmDevice device, List<ChannelGroupType> groupTypes) {
        String label = MetadataUtils.getDeviceName(device);
        String description = String.format("%s (%s)", label, device.getType());

        List<String> supportedBridgeTypeUids = new ArrayList<String>();
        supportedBridgeTypeUids.add(THING_TYPE_BRIDGE.toString());
        ThingTypeUID thingTypeUID = UidUtils.generateThingTypeUID(device);

        Map<String, String> properties = new HashMap<String, String>();
        properties.put(Thing.PROPERTY_VENDOR, PROPERTY_VENDOR_NAME);
        properties.put(Thing.PROPERTY_MODEL_ID, device.getType());

        URI configDescriptionURI = getConfigDescriptionURI(device);
        if (configDescriptionProvider.getConfigDescription(configDescriptionURI, null) == null) {
            generateConfigDescription(device, configDescriptionURI);
        }

        List<ChannelGroupDefinition> groupDefinitions = new ArrayList<ChannelGroupDefinition>();
        for (ChannelGroupType groupType : groupTypes) {
            String id = StringUtils.substringAfterLast(groupType.getUID().getId(), "_");
            groupDefinitions.add(new ChannelGroupDefinition(id, groupType.getUID()));
        }

        return new ThingType(thingTypeUID, supportedBridgeTypeUids, label, description, null, groupDefinitions,
                properties, configDescriptionURI);
    }

    /**
     * Creates the ChannelType for the given datapoint.
     */
    private ChannelType createChannelType(HmDatapoint dp, ChannelTypeUID channelTypeUID) {
        ChannelType channelType;
        if (dp.getName().equals(DATAPOINT_NAME_LOWBAT)) {
            channelType = DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_LOW_BATTERY;
        } else if (dp.getName().equals(DATAPOINT_NAME_RSSI_DEVICE)) {
            channelType = DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_SIGNAL_STRENGTH;
        } else {
            String itemType = MetadataUtils.getItemType(dp);
            String category = MetadataUtils.getCategory(dp, itemType);
            String label = MetadataUtils.getLabel(dp);
            String description = MetadataUtils.getDatapointDescription(dp);

            List<StateOption> options = MetadataUtils.generateOptions(dp, new OptionsBuilder<StateOption>() {
                @Override
                public StateOption createOption(String value, String description) {
                    return new StateOption(value, description);
                }
            });

            StateDescription state = null;
            if (dp.isNumberType()) {
                BigDecimal min = MetadataUtils.createBigDecimal(dp.getMinValue());
                BigDecimal max = MetadataUtils.createBigDecimal(dp.getMaxValue());
                BigDecimal step = MetadataUtils.createBigDecimal(dp.isFloatType() ? new Float(0.1) : 1L);
                state = new StateDescription(min, max, step, MetadataUtils.getStatePattern(dp), dp.isReadOnly(),
                        options);
            } else {
                state = new StateDescription(null, null, null, MetadataUtils.getStatePattern(dp), dp.isReadOnly(),
                        options);
            }

            channelType = new ChannelType(channelTypeUID, !MetadataUtils.isStandard(dp), itemType, label, description,
                    category, null, state, configDescriptionUriChannel);

        }
        return channelType;
    }

    private void generateConfigDescription(HmDevice device, URI configDescriptionURI) {
        List<ConfigDescriptionParameter> parms = new ArrayList<ConfigDescriptionParameter>();
        List<ConfigDescriptionParameterGroup> groups = new ArrayList<ConfigDescriptionParameterGroup>();

        for (HmChannel channel : device.getChannels()) {
            String groupName = "HMG_" + channel.getNumber();
            String groupLabel = MetadataUtils.getDescription("CHANNEL_NAME") + " " + channel.getNumber();
            groups.add(new ConfigDescriptionParameterGroup(groupName, null, false, groupLabel, null));

            for (HmDatapoint dp : channel.getDatapoints().values()) {
                if (dp.getParamsetType() == HmParamsetType.MASTER) {
                    ConfigDescriptionParameterBuilder builder = ConfigDescriptionParameterBuilder.create(
                            MetadataUtils.getParameterName(dp), MetadataUtils.getConfigDescriptionParameterType(dp));

                    builder.withLabel(MetadataUtils.getLabel(dp));
                    builder.withDefault(ObjectUtils.toString(dp.getDefaultValue()));
                    builder.withDescription(MetadataUtils.getDatapointDescription(dp));

                    if (dp.isEnumType()) {
                        builder.withLimitToOptions(dp.isEnumType());
                        List<ParameterOption> options = MetadataUtils.generateOptions(dp,
                                new OptionsBuilder<ParameterOption>() {
                                    @Override
                                    public ParameterOption createOption(String value, String description) {
                                        return new ParameterOption(value, description);
                                    }
                                });
                        builder.withOptions(options);
                    }

                    if (dp.isNumberType()) {
                        builder.withMinimum(MetadataUtils.createBigDecimal(dp.getMinValue()));
                        builder.withMaximum(MetadataUtils.createBigDecimal(dp.getMaxValue()));
                        builder.withStepSize(MetadataUtils.createBigDecimal(dp.isFloatType() ? new Float(0.1) : 1L));
                        builder.withUnitLabel(MetadataUtils.getUnit(dp));
                    }

                    builder.withPattern(MetadataUtils.getPattern(dp));
                    builder.withGroupName(groupName);
                    parms.add(builder.build());
                }
            }
        }
        if (!parms.isEmpty()) {
            configDescriptionProvider.addConfigDescription(new ConfigDescription(configDescriptionURI, parms, groups));
        }

    }

    private URI getConfigDescriptionURI(HmDevice device) {
        try {
            return new URI(String.format("%s:%s", CONFIG_DESCRIPTION_URI_THING, UidUtils.generateThingTypeUID(device)));
        } catch (URISyntaxException ex) {
            logger.warn("Can't create configDescriptionURI for device type " + device.getType());
            return null;
        }
    }

    /**
     * Returns true, if the given datapoint is a Thing status.
     */
    public static boolean isStatusDatapoint(HmDatapoint dp) {
        return StringUtils.indexOfAny(dp.getName(), STATUS_DATAPOINT_NAMES) != -1;
    }

    /**
     * Returns true, if the given datapoint can be ignored for metadata generation.
     */
    public static boolean isIgnoredDatapoint(HmDatapoint dp) {
        return StringUtils.indexOfAny(dp.getName(), IGNORE_DATAPOINT_NAMES) != -1 || isStatusDatapoint(dp);
    }

}
