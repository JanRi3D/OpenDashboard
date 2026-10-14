package com.adayo.canproxy.bean;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Wire keys for ICanboxInterface.sendDatas(Map). The OEM enum is Parcelable and writes
 * its ordinal. These explicit IDs refer to the FULL OEM enum, never to this subset's ordinal.
 * The receiver loads its own enum using this exact class name and reconstructs values()[id].
 */
public enum VehicleSettingType implements Parcelable {
    ICE3_CMD_CTRLDRISEATHEAT_LEVEL(14),
    ICE3_CMD_CTRLDRISEATVTL_LEVEL(15),
    ICE3_CMD_CTRLPASSSEATHEAT_LEVEL(55),
    ICE3_CMD_CTRLPASSSEATVTL_LEVEL(56),
    ICE15_Set_FLSHeat_Lvl(93),
    ICE15_Set_FRSHeat_Lvl(94),
    ICE15_Set_FLSVtl_Lvl(97),
    ICE15_Set_FRSVtl_Lvl(98);

    private final int wireId;
    VehicleSettingType(int wireId) { this.wireId = wireId; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeInt(wireId); }

    public static final Parcelable.Creator<VehicleSettingType> CREATOR =
            new Parcelable.Creator<VehicleSettingType>() {
                @Override public VehicleSettingType createFromParcel(Parcel source) {
                    int id = source.readInt();
                    for (VehicleSettingType value : values()) if (value.wireId == id) return value;
                    throw new IllegalArgumentException("Unknown seat command: " + id);
                }
                @Override public VehicleSettingType[] newArray(int size) { return new VehicleSettingType[size]; }
            };
}
