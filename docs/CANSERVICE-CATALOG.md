# CanService signal catalog (ADAYO AC822X)

Supplied by the project owner on 2026-10-09. Read a signal by GROUP + ID. IDs repeat between
groups and are not CAN frame IDs. The original signal names are preserved, including their
spelling. OpenDashboard's use of it: [INTEGRATIONS.md §4](INTEGRATIONS.md).

## Native head-unit connection

- Service package: `com.adayo.canservice`
- Bind action: `com.adayo.can.canservice.action`
- Binder descriptor: `com.adayo.canproxy.binder.service.ICanboxInterface`
- Indexed reads: `writeInterfaceToken`, `writeInt(signal ID)`, `transact(flags=0)`,
  `readException`, then `readFloat` (or `readInt` for ConfigInfo).
- Float wrapper failures return NaN; ConfigInfo failures return -2147483648.

## BATTINFO — battery, charging and battery heating

11 entries; Binder transaction 16; returns float.

| ID | Signal |
|---|---|
| 0 | VCM_CCStatus |
| 1 | BookingSetStatus |
| 2 | BookBattHeat_OnOff |
| 3 | VCM_BookingReqStatus |
| 4 | BMS_BatteryChargeStatus |
| 5 | VCM1_Fdbk_DrivingBattHeatState |
| 6 | BMS6_Status_ChgSOCSetting |
| 7 | VCM1_Fdbk_EnduranDisplay_Mode |
| 8 | VCM_PRAMainRelayPLStatus |
| 9 | VCM_PRAMainRelayMIStatus |
| 10 | HCU4_Data_BattSOC |

## BODYDETAILSINFO — vehicle state, doors, fuel, range, warnings and faults

116 entries; Binder transaction 6; returns float.

| ID | Signal | ID | Signal |
|---|---|---|---|
| 0 | IGN_KEY_POS | 58 | DAS3_STATUS_TSR |
| 1 | SHIFT_LEVER_POSITION | 59 | IC3_STATUS_ADAS_FAULT |
| 2 | BCM_FRONT_MACHINE_COVER | 60 | IC3_STATUS_EPS_FAULT |
| 3 | BCM_LUGGAGEDOORST | 61 | IC3_STATUS_ABS_FAULT |
| 4 | BCM_RRDOORST | 62 | IC3_STATUS_ESP_FAULT |
| 5 | BCM_RLDOORST | 63 | IC3_STATUS_APB_FAULT |
| 6 | BCM_PASSDOORST | 64 | IC3_STATUS_BRAKESYS_FAULT |
| 7 | BCM_DRIVERDOORST | 65 | IC3_STATUS_SRS_FAULT |
| 8 | EMS_IND_STARTSTOPSTATUS_ONOFF | 66 | IC3_STATUS_AGS_FAULT |
| 9 | EMS_IND_STARTSTOPMAINSWT_ONOFF | 67 | IC3_STATUS_TCU_FAULT |
| 10 | ODOMETER_VALUE | 68 | IC3_STATUS_HEVSYS_FAULT |
| 11 | ENGINE_SPEED | 69 | IC3_STATUS_ADASCAMERA_DIRTY |
| 12 | IC2_STATUS_ENDURMILEAGEVALUE | 70 | IC3_STATUS_AUTOHOLD_FAULT |
| 13 | FUEL_LEVEL_VALUE | 71 | IC3_STATUS_HDC_FAULT |
| 14 | BCM1_RAIN_INTENSITY_BS3 | 72 | IC3_STATUS_SVA_FAULT |
| 15 | ENGINE_STATUS_BS3 | 73 | IC3_STATUS_ACFAULT_LVL |
| 16 | DAS3_STATUS_FCW | 74 | IC3_STATUS_PLG_FAULT |
| 17 | DAS2_STATUS_ACC | 75 | IC3_STATUS_STARTBUTTON_FAULT |
| 18 | DAS3_STATUS_IHC | 76 | IC3_STATUS_KEYBATTERY_LOW |
| 19 | DAS2_STATUS_AEB | 77 | ESP2_STATUS_LOWFLUID_ACTVINACTV |
| 20 | APA_INFODISPLAYREQ | 78 | ESP2_LOWFLUID_VALIDITY |
| 21 | APA_PARKINGBARPERCENT | 79 | EPS1_STATUS_STEERINGHANDFEEL |
| 22 | APA_PARKINGBARSTS | 80 | IC6_STATUS_GPFREGENERATION |
| 23 | FUEL_LEVEL_WARNING | 81 | IC6_STATUS_GEARBOXTEMP_HIGH |
| 24 | ENGINE5_IND_LOENGOILPRES_ONOFF | 82 | IC6_STATUS_STSTARTSTOP_FAULT |
| 25 | ESP1_STATUS_LOWFLUID_ACTVINACTV | 83 | IC6_STATUS_CHARGING |
| 26 | CRASH_OUTPUT_STS | 84 | IC6_STATUS_POWERBATTERY_FAULT |
| 27 | IC3_STATUS_LOENGOILPRES | 85 | IC6_STATUS_POWERBATTERYTEMP_HIGH |
| 28 | IC3_STATUS_BATTCHRGFAULT | 86 | IC6_STATUS_MOTOR_FAULT |
| 29 | IC3_STATUS_HIENGCLTTEMP | 87 | IC6_STATUS_MOTORTEMP_HIGH |
| 30 | IC3_STATUS_LOENGCLTLEVEL | 88 | IC6_STATUS_HIGHVOLTAGE_DISCONNECT |
| 31 | IC3_STATUS_LOBRKFLUIDLEVEL | 89 | IC6_STATUS_POWERSYSTEM_FAULT |
| 32 | IC3_STATUS_LOCLEANFLUIDLEVEL | 90 | IC6_STATUS_VSPSYSTEM_FAULT |
| 33 | IC3_STATUS_ENGINEFAULT | 91 | IC6_STATUS_LIMITPOWERMODE |
| 34 | IC3_STATUS_ENGEMIFAULT | 92 | TCU3_IND_SHIFTERMISUSD |
| 35 | DAS2_STATUS_MCS_ERROR | 93 | STEERING_ANGLE_VALID |
| 36 | LDWS1_STATUS_SYSTEM_LDWS | 94 | STEERING_ANGLE |
| 37 | SVA1_FAULT_SYSTEM_NRMERR | 95 | PEPS_SHIFT_POSITION_ALERT |
| 38 | ABS_FAULT | 96 | PEPS_PRESS_BRAKE_PEDAL |
| 39 | EBD_FAULT | 97 | PEPS_DOOR_LOCK_IG_NOTOFF_ALERT |
| 40 | PEPS_RM_KEY_BATTERY_ST | 98 | PEPS1_RQ_KEEPSHIFTERFORPARK |
| 41 | PEPS_CID_NOT_INDENFIED_ALERT | 99 | PEPS1_RQ_STEPCLUTCHFORSTART |
| 42 | PEPS_ONE_KEYSTART_SWITH_ERROR | 100 | APB_IND_WARNINGMESSAGE |
| 43 | PEPS_CID_LEFT_ALERT | 101 | IS_MAUNNUL |
| 44 | EPS_FAIL_STS | 102 | APB_STATUS |
| 45 | VDC_TCS_FAULT | 103 | VCM1_STATUS_SHIFTREMINDER |
| 46 | BRS_IND_SYSTEMFAULT | 104 | BMS6_Status_SOCForICDisp |
| 47 | AIR_BAG_FAIL_STS | 105 | JRadar1_Warn_DOWLeft_Lvl |
| 48 | PLG1_STATUS_CURRENTSTATE_MODE | 106 | JRadar1_Warn_DOWRight_Lvl |
| 49 | TRANS_FAULT | 107 | BMS_BatteryChargeStatus |
| 50 | EMS_IND_STARTSTOPFAULT_ONOFF | 108 | BMS_SOC |
| 51 | APB_IND_ERROR | 109 | BCM1_Status_AntiThief |
| 52 | APB_IND_AVHERROR | 110 | SBM1_IND_LegalVirtKeyAreaLevel |
| 53 | HDC_HDCFUNCTIONAVAILABLE | 111 | VCM3_Fdbk_DriveModeRemember_OnOff |
| 54 | AGS1_FAULT_NRMFAULT | 112 | SWCM1_Fdbk_SWHeat_Lvl |
| 55 | BCM1_STATUS_ANTITHIEF | 113 | ECALL1_CMD_Mute |
| 56 | PEPS2_STATUS_STATEMACHINE | 114 | Msg_ECALL1_4EA |
| 57 | DAS3_STATUS_LDW | 115 | DAS3_Fdbk_LDP_OnOff |

## BODYCCSINFO — windows, roof, seats, mirrors, lighting, drive modes, driver assistance

122 entries; Binder transaction 7; returns float.

| ID | Signal | ID | Signal |
|---|---|---|---|
| 0 | BCM2_FDBK_SETRELOCKING | 61 | OSAL1_Fault_LINResp_Error |
| 1 | BCM2_FDBK_SETVEHICLESPDLOCK | 62 | OSAL1_IND_LogoOutsideALCfg |
| 2 | PEPS2_FDBK_WELCOME | 63 | OSAL1_Status_OutsideAL_OnOff |
| 3 | BCM2_FDBK_BATTSAVERTIME | 64 | VCM_OnePedalMode |
| 4 | BCM2_FDBK_ROOFLAMPSLIGHTTIME | 65 | ESP2_Fdbk_CST_OnOff |
| 5 | BCM2_FDBK_LOCKCARSOUND | 66 | VCM3_DriveMode |
| 6 | FDBKREMOTELOCKKEY | 67 | VCM6_MbRegenMaxTqMode |
| 7 | BCM2_FDBK_AUTOFOLDMIRROR | 68 | VCM1_Fdbk_ECOPlusMode_OnOff |
| 8 | APM1_STATUE_WINPOSTION_FL | 69 | VSP1_Status_VSP_OnOff |
| 9 | APM1_STATUE_WINPOSTION_FR | 70 | BCM2_Fdbk_RLSAutoClsWinFunc |
| 10 | APM1_STATUE_WINPOSTION_RL | 71 | EHB1_Fdbk_BrakeModeStatus |
| 11 | APM1_STATUE_WINPOSTION_RR | 72 | AL_Fdbk_ALBrightness_Lvl |
| 12 | APM1_STATUS_FLWINDOW | 73 | AL_Fdbk_AmbLight_Mode |
| 13 | APM1_STATUS_FRWINDOW | 74 | RLmp1_Fdbk_LightSignal_Mode |
| 14 | APM1_STATUS_RLWINDOW | 75 | RLmp1_Fdbk_MusicFollow |
| 15 | APM1_STATUS_RRWINDOW | 76 | FLmp1_Fdbk_CorLmpTrnOnWthLoBeam |
| 16 | BCM3_STATUS_SRPOS | 77 | AL_Validity_Config |
| 17 | BCM3_STATUS_SRMOVEMENT | 78 | DrAL1_IND_Config_Valid |
| 18 | BCM3_STATUS_SSPOS | 79 | RfAL1_IND_Config_Valid |
| 19 | BCM3_STATUS_SSMOVEMENT | 80 | BCMRKEID_Data_RKEID_1 |
| 20 | APM2_STATUS_DRISEATHEAT_ONOFF | 81 | BCMRKEID_Data_RKEID_2 |
| 21 | APM2_STATUS_DRISEATHEAT_LEVEL | 82 | BCMRKEID_Data_RKEID_3 |
| 22 | APM2_STATUS_PASSSEATHEAT_ONOFF | 83 | BCMRKEID_Data_RKEID_4 |
| 23 | APM2_STATUS_PASSSEATHEAT_LEVEL | 84 | RDCM1_Data_RRMirrorPos_X |
| 24 | DAS3_FDBK_AEB_ONOFF | 85 | RDCM1_Data_RRMirrorPos_Y |
| 25 | DAS3_FDBK_IHC_ONOFF | 86 | RDCM1_Data_LRMirrorPos_X |
| 26 | DAS3_FDBK_TSR_ONOFF | 87 | RDCM1_Data_LRMirrorPos_Y |
| 27 | DAS3_FDBK_FCWSENSITIVITY_LEVEL | 88 | SCFL1_Data_FLSHorzPos_Value |
| 28 | DAS3_FDBK_FCW_ONOFF | 89 | SCFL1_Data_FLSVertPos_Value |
| 29 | DAS3_FDBK_LDW_ONOFF | 90 | SCFL1_Data_FLSBckrstPos_Value |
| 30 | SVA1_FDBK_SYSTEM_ONOFF | 91 | IC5_Status_ICDisVehSpd_Value |
| 31 | LDWS1_FDBK_LDWSSTATUS_ONOFF | 92 | JRadar1_Warn_RCTALeft_Lvl |
| 32 | LDWS1_FDBK_SETWARNINGDISTANCE | 93 | JRadar1_Warn_RCTARight_Lvl |
| 33 | CRUISE_MAIN_LAMP | 94 | SCFL2_Fdbk_FLSHeat_Lvl |
| 34 | CRUISE_TARGET_SPEED_HIGH | 95 | SCFR1_Fdbk_FRSHeat_Lvl |
| 35 | CRUISE_TARGET_SPEED_LOW | 96 | SCSL2_Fdbk_SLSHeat_Lvl |
| 36 | CRUISE_CONTROL_STATUS | 97 | SCSL4_Fdbk_SRSHeat_Lvl |
| 37 | DAS3_SET_CRUISESPEED_VALUE_BS3 | 98 | SCFL2_Fdbk_FLSVtl_Lvl |
| 38 | DAS2_STATUS_ACC | 99 | SCFR1_Fdbk_FRSVtl_Lvl |
| 39 | DAS3_SET_ACCHEADWAY_BS3 | 100 | SCSL2_Fdbk_SLSVtl_Lvl |
| 40 | FDBK_COLORR | 101 | SCSL4_Fdbk_SRSVtl_Lvl |
| 41 | FDBK_COLORR_VALUE | 102 | SCFL2_Fdbk_FLSAutoHeatFunc_OnOff |
| 42 | FDBK_COLORG_VALUE | 103 | SCFR1_Fdbk_FRSAutoHeatFunc_OnOff |
| 43 | FDBK_COLORB_VALUE | 104 | SCSL2_Fdbk_SLSAutoHeatFunc_OnOff |
| 44 | AL_FDBK_ALBRIGHTNESS_LVL | 105 | SCSL4_Fdbk_SRSAutoHeatFunc_OnOff |
| 45 | AL_IND_SETALCOLORFDBK_VALID | 106 | SCFL2_Fdbk_FLSAutoVtlFunc_OnOff |
| 46 | PLG1_FDBK_PLGOPEN_LVL | 107 | SCFR1_Fdbk_FRSAutoVtlFunc_OnOff |
| 47 | DAS3_FDBK_LDWSENSITIVITY_LEVEL | 108 | SCSL2_Fdbk_SLSAutoVtlFunc_OnOff |
| 48 | LDCM1_STATUS_HEADLAMPHEIGHT_LVL | 109 | SCSL4_Fdbk_SRSAutoVtlFunc_OnOff |
| 49 | SHM_STATUS_DRISEATHEAT_LEVEL | 110 | SCFL1_Fdbk_FSCvtGetOnAndOffVeh |
| 50 | SHM_STATUS_DRISEATVTL_LEVEL | 111 | SCFL2_Fdbk_FLSAutoHeatTemp |
| 51 | SMM1_STATUS_DRISEATMSGSTR_LVL | 112 | SCFL2_Fdbk_FLSAutoVtlTemp |
| 52 | SHM_REP_ERROR | 113 | LDCM1_Status_LRMirRevDwnFunc |
| 53 | SHM_STATUS_LIN | 114 | SBM3_Fdbk_BLEOffCarAutoLockFunc |
| 54 | SMM1_STATUS_DRISEATMSG_MODE | 115 | JRadar1_Fdbk_BSD_OnOff |
| 55 | SMM1_STATUS_PASSSEATMSG_MODE | 116 | JRadar1_Fdbk_BSDWarn_OnOff |
| 56 | SHM_STATUS_PASSSEATHEAT_LEVEL | 117 | JRadar1_Fdbk_DOW_OnOff |
| 57 | SHM_STATUS_PASSSEATVTL_LEVEL | 118 | JRadar1_Fdbk_DOWWarn_OnOff |
| 58 | SMM1_STATUS_PASSSEATMSGSTR_LVL | 119 | JRadar1_Fdbk_RCTA_OnOff |
| 59 | SMM1_FAULT_LINRESP_ERROR | 120 | CPD1_Fdbk_PermanentFuncSwt |
| 60 | LDCM1_STATUS_LRMIRROR_FOLDUNFOLD | 121 | LDCM1_FDBK_AUTOFOLDLRMIRROR |

## TPMSINFO — tyre pressure warnings and monitoring status

15 entries; Binder transaction 8; returns float.

| ID | Signal |
|---|---|
| 0 | LF_PRESSURE_WARNING |
| 1 | RF_PRESSURE_WARNING |
| 2 | RR_PRESSURE_WARNING |
| 3 | LR_PRESSURE_WARNING |
| 4 | TPMS_LAMP_STATUS |
| 5 | TPMS_0X3E0 |
| 6 | IC6_STATUS_TIREPRESSURE_HIGH |
| 7 | IC6_STATUS_TIRELEAKFASTLOWPRES |
| 8 | IC6_STATUS_TIREPRESSURE_LOW |
| 9 | IC6_STATUS_LEAKINGFAST |
| 10 | IC6_STATUS_TIRETEMP_HIGH |
| 11 | IC6_STATUS_TPMSSENSBATT_LOW |
| 12 | IC6_STATUS_TPMSSENS_LOSS |
| 13 | IC6_STATUS_TPMSSENS_FAULT |
| 14 | IC6_STATUS_TPMSNOTMATCH |

## RADARINFO — parking sensor levels, faults and status

35 entries; Binder transaction 9; returns float.

| ID | Signal | ID | Signal |
|---|---|---|---|
| 0 | PDC1_FAULT_SENSFL_NRMERR | 18 | PDC1_STATUS_DISTANCERML_LVL |
| 1 | PDC1_FAULT_SENSFR_NRMERR | 19 | PDC1_STATUS_DISTANCERMR_LVL |
| 2 | PDC1_FAULT_SENSFML_NRMERR | 20 | PDC1_FAULT_SENSFL_PSC |
| 3 | PDC1_FAULT_SENSFMR_NRMERR | 21 | PDC1_FAULT_SENSFR_PSC |
| 4 | PDC1_FAULT_SENSRL_NRMERR | 22 | PDC1_FAULT_SENSRL_PSC |
| 5 | PDC1_FAULT_SENSRML_NRMERR | 23 | PDC1_FAULT_SENSRR_PSC |
| 6 | PDC1_FAULT_SENSRMR_NRMERR | 24 | PDC1_INDICATION_MINDISTANCE_INFO |
| 7 | PDC1_FAULT_SENSRR_NRMERR | 25 | FRONT_REAR_RADAR_SIGNAL_LOST |
| 8 | PDC1_STATUS_PARKVIEW_ONOFF | 26 | PSC2_STATUS_FLANKDISTANCEFL_LVL |
| 9 | PDC1_STATUS_FRONTSENS_ONOFF | 27 | PSC2_STATUS_FLANKDISTANCEFR_LVL |
| 10 | PDC1_FAULT_SYSTEM_NRMERR | 28 | PSC2_STATUS_FLANKDISTANCERL_LVL |
| 11 | PDC1_NUM_RADAR | 29 | PSC2_STATUS_FLANKDISTANCERR_LVL |
| 12 | PDC1_STATUS_DISTANCEFL_LVL | 30 | PSC2_STATUS_FLANKDISTANCELMF_LVL |
| 13 | PDC1_STATUS_DISTANCEFR_LVL | 31 | PSC2_STATUS_FLANKDISTANCELMR_LVL |
| 14 | PDC1_STATUS_DISTANCEFML_LVL | 32 | PSC2_STATUS_FLANKDISTANCERMF_LVL |
| 15 | PDC1_STATUS_DISTANCEFMR_LVL | 33 | PSC2_STATUS_FLANKDISTANCERMR_LVL |
| 16 | PDC1_STATUS_DISTANCERL_LVL | 34 | FLANK_RADAR_SIGNAL_LOST |
| 17 | PDC1_STATUS_DISTANCERR_LVL | | |

## CONFIGINFO — vehicle type and configuration or equipment flags

68 entries; Binder transaction 10; returns int.

| ID | Flag | ID | Flag | ID | Flag | ID | Flag |
|---|---|---|---|---|---|---|---|
| 0 | VEHICLE_TYPE | 17 | TSR | 34 | ADJHEADLAMPHEIGHT | 51 | JRADAR1 |
| 1 | STARTSTOP | 18 | SUNROOF | 35 | CSTSwitch | 52 | BRM |
| 2 | WELCOME | 19 | SAS1 | 36 | AC | 53 | MIDDLE_HEAT |
| 3 | AUTOFOLD_MIRROR | 20 | EPS1 | 37 | POWER_TYPE | 54 | MIDDLE_VTL |
| 4 | REAR_AC | 21 | AC2_CONFIGURE_EV_AC_TYPE | 38 | RLS | 55 | RADAR |
| 5 | DRI_SEATHEAT | 22 | AC1_CONFIGURE_DUALSINGLE | 39 | BMS | 56 | SEATHEAT_1L |
| 6 | PASS_SEATHEAT | 23 | WINDOW_VOICE_CTRL | 40 | DrAL_RfAL | 57 | SEATWIND_1L |
| 7 | CHARGEBOOKING | 24 | PDC | 41 | Lantern_language_mode | 58 | SEATHEAT_1R |
| 8 | DISPLAY_RESOLUTION | 25 | DVR | 42 | OSAL_MUSIC | 59 | SEATWIND_1R |
| 9 | DRI_SEATWIND | 26 | PLG | 43 | OSAL_switch | 60 | SEATHEAT_2L |
| 10 | PASS_SEATWIND | 27 | AQS | 44 | Corner_lamp | 61 | SEATWIND_2L |
| 11 | DRI_SEATMASS | 28 | BSD | 45 | Personality_memory | 62 | SEATHEAT_2R |
| 12 | PASS_SEATMASS | 29 | LDW | 46 | AVM1 | 63 | SEATWIND_2R |
| 13 | IS_CONFIG | 30 | IC2 | 47 | PDC1_RADAR_NUMBER | 64 | STEERINGWHEEL_HEAT |
| 14 | FCW | 31 | OutsideAL | 48 | BT_KEY | 65 | AVM_FRAME_RATE |
| 15 | AEB | 32 | VSP | 49 | LCA | 66 | LDP |
| 16 | IHC | 33 | LRMIRROR_FOLDUNFOLD | 50 | DOW | 67 | DAB |

## Standalone vehicle reads

- Vehicle speed: ICanboxInterface transaction 11, no signal-ID argument, `readFloat`.
- VIN: ICanboxInterface transaction 12, no signal-ID argument, `readString`.

## Climate reads

Get the AC child binder with ICanboxInterface transaction 1 (no ID argument). Child descriptor
`com.adayo.canproxy.binder.service.IACInterface`. Read each ID with child transaction 4,
`writeInt(ID)`, `readFloat`. Labels are inferred from how the source app displays and uses the
values.

| ID | Meaning (inferred) |
|---|---|
| 0 | Front windscreen defrost |
| 2 | A/C on/off flag |
| 3 | Climate system on/off |
| 4 | Automatic climate mode |
| 8 | Air outlet mode |
| 9 | Air recirculation |
| 12 | Fan speed |
| 16 | Outside temperature |
| 17 | Selected climate temperature |
| 21 | Rear window defrost |
| 31 | Cooling flag used by the COOL quick button |
| 38 | Cabin temperature |

Climate ID 31 is read by the COOL quick button; the panel reads ID 2 for A/C. Their
equivalence and all vehicle-specific value encodings are unconfirmed.

## Notes from the source

- Fuel: BodyDetailsInfo 13 is accepted as 0–100 percent. Fuel litres are estimated from the
  configured tank capacity. A fresh valid optional OBD fuel reading takes priority in the source app.
- TPMSInfo provides warnings/status; numerical tyre data is OBD only.
- Parking sensor LVL values have no verified distance units.
- Configuration flags do not establish that equipment can be activated.
- The source app's 42 dashboard fields list head-unit sources for: SOC, range, charging state,
  selected/outside/cabin temperature, climate state, speed, gear, fuel % / litres, phone status
  and Bluetooth track. 12 V voltage, coolant temperature, odometer, rpm, engine load, intake
  temperature and tyre pressures/temperatures are optional OBD there (standard PIDs 0142, 0105,
  010C, 0104, 010F, 012F, 010D).
