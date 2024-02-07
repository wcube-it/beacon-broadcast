package com.acer.paroyalty;

public enum  Status {
    SUCCESS,   // 成功
    LICENSE_ERROR,  //  appid 或是 appSecretKey 錯誤
    LICENSE_EXPIRED,  // LICENSE 過期
    NETWROK_ERROR,   // 網路Error
    OTHERS_ERROR   // 其他錯誤
}
