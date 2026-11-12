package com.example.sclerago.data

import com.example.sclerago.services.media.Media

/**
 * Reference photos for the sample assets: freely licensed images from Wikimedia Commons,
 * loaded over the network and cached on the device. Each carries its author and licence.
 */
private fun web(id: String, url: String, author: String, licence: String) =
    Media(id, url, video = false, createdAt = 0L, credit = "Photo: $author · $licence · Wikimedia Commons")

val SEED_PHOTOS: Map<Int, List<Media>> = mapOf(
    1 to listOf(
        web("seed-1-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e9/Three_Phase_Induction_Motor.jpg/960px-Three_Phase_Induction_Motor.jpg", "KishanMalaviyaatCHETAK ELECTRICALS", "CC BY-SA 4.0"),
        web("seed-1-1", "https://upload.wikimedia.org/wikipedia/commons/8/89/Motors01CJC.jpg", "en:User:C_J_Cowie", "CC BY-SA 3.0"),
    ),
    2 to listOf(
        web("seed-2-0", "https://upload.wikimedia.org/wikipedia/commons/6/69/KSB_09_Etanorm_mit_Motor_und_Frequenzumrichter.jpg", "KSB Aktiengesellschaft, Frankenthal", "CC BY-SA 3.0"),
        web("seed-2-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cd/Warman_centrifugal_pump.jpg/960px-Warman_centrifugal_pump.jpg", "Bernard S. Janse", "CC BY 2.5"),
    ),
    9 to listOf(
        web("seed-9-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8c/Inline_Centrifugal_Fan.jpg/960px-Inline_Centrifugal_Fan.jpg", "Saud", "CC BY-SA 4.0"),
        web("seed-9-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Dornbirn-Haemmerle-mechanical_fan_%28roof%29-01ASD.jpg/960px-Dornbirn-Haemmerle-mechanical_fan_%28roof%29-01ASD.jpg", "Asurnipal", "CC BY-SA 4.0"),
        web("seed-9-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c4/Extractor_fan_12.jpg/960px-Extractor_fan_12.jpg", "Antonio Mette", "CC BY-SA 4.0"),
    ),
    3 to listOf(
        web("seed-3-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fc/Air_Cooled_Liquid_Chiller.jpg/960px-Air_Cooled_Liquid_Chiller.jpg", "Saud", "CC BY-SA 4.0"),
        web("seed-3-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cd/Trane_AquaStream_3G_chiller_%282%29.jpg/960px-Trane_AquaStream_3G_chiller_%282%29.jpg", "Cjp24", "CC BY-SA 4.0"),
        web("seed-3-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e2/Chiller_%2819270066402%29.jpg/960px-Chiller_%2819270066402%29.jpg", "beeveephoto from Belgium", "CC BY-SA 2.0"),
    ),
    4 to listOf(
        web("seed-4-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/f/ff/HVAC_Air_Handler_Unit%2C_pic1.JPG/960px-HVAC_Air_Handler_Unit%2C_pic1.JPG", "Alf van Beem", "CC0"),
        web("seed-4-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1b/Air_handling_units_in_large_commercial_building%2C_Brisbane.jpg/960px-Air_handling_units_in_large_commercial_building%2C_Brisbane.jpg", "Kgbo", "CC BY-SA 4.0"),
        web("seed-4-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/00/Air_handling_unit.JPG/960px-Air_handling_unit.JPG", "P199", "Public domain"),
    ),
    8 to listOf(
        web("seed-8-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b8/Fire_pumps_plantroom%2C_Sydney%2C_2022.jpg/960px-Fire_pumps_plantroom%2C_Sydney%2C_2022.jpg", "Kgbo", "CC BY-SA 4.0"),
        web("seed-8-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/11/Fire_Pump_Room%2C_Brisbane%2C_2021.jpg/960px-Fire_Pump_Room%2C_Brisbane%2C_2021.jpg", "Kgbo", "CC BY-SA 4.0"),
        web("seed-8-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/85/Fire_pump%2C_Kabalulumana_Hostel%2C_Mount_Isa%2C_2023.jpg/960px-Fire_pump%2C_Kabalulumana_Hostel%2C_Mount_Isa%2C_2023.jpg", "Kgbo", "CC BY-SA 4.0"),
    ),
    7 to listOf(
        web("seed-7-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d0/Distribution_board_in_Slovakia_05.jpg/960px-Distribution_board_in_Slovakia_05.jpg", "Oto Zapletal", "CC BY 4.0"),
        web("seed-7-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d3/Fuse_box_on_hager_components.JPG/960px-Fuse_box_on_hager_components.JPG", "Dmitry G", "CC BY-SA 3.0"),
        web("seed-7-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/41/Dornbirn-Haemmerle-distribution_board-01ASD.jpg/960px-Dornbirn-Haemmerle-distribution_board-01ASD.jpg", "Asurnipal", "CC BY-SA 4.0"),
    ),
    5 to listOf(
        web("seed-5-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5a/Programmable_logic_controller.jpg/960px-Programmable_logic_controller.jpg", "Unknown", "CC BY-SA 3.0"),
        web("seed-5-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ea/PLC_Panasonic_FP-XH_C30T.jpg/960px-PLC_Panasonic_FP-XH_C30T.jpg", "Angga Panca Alam Anugrah", "CC BY-SA 4.0"),
    ),
    6 to listOf(
        web("seed-6-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bc/AxisCCTV.jpg/960px-AxisCCTV.jpg", "Raysonho @ Open Grid Scheduler / Grid Engine", "CC0"),
        web("seed-6-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1b/BoschCCTV.jpg/960px-BoschCCTV.jpg", "Raysonho @ Open Grid Scheduler / Grid Engine", "CC0"),
        web("seed-6-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4a/CCTV.jpg/960px-CCTV.jpg", "JensPersson", "CC BY-SA 3.0"),
    ),
    10 to listOf(
        web("seed-10-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Ruckus_H510_Access_Point.jpg/960px-Ruckus_H510_Access_Point.jpg", "TaurusEmerald", "CC BY-SA 4.0"),
        web("seed-10-1", "https://upload.wikimedia.org/wikipedia/commons/5/58/Access_Point_UniFi.jpg", "Sabbo91", "CC BY-SA 4.0"),
        web("seed-10-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/81/Access-point-wireless.jpg/960px-Access-point-wireless.jpg", "Rodrigo César", "Public domain"),
    ),
    12 to listOf(
        web("seed-12-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0d/Dell_OptiPlex_3060_SFF.jpg/960px-Dell_OptiPlex_3060_SFF.jpg", "TheDragonFire300", "CC BY 4.0"),
        web("seed-12-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bf/Dell_OptiPlex_5060_Micro.jpg/960px-Dell_OptiPlex_5060_Micro.jpg", "Jackpen7", "CC BY-SA 4.0"),
        web("seed-12-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d1/Dell_OptiPlex_Series_4_.jpg/960px-Dell_OptiPlex_Series_4_.jpg", "PolarisLakewell", "CC BY-SA 4.0"),
    ),
    13 to listOf(
        web("seed-13-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/71/Cisco_IP_Phone_7945_P1110468.jpg/960px-Cisco_IP_Phone_7945_P1110468.jpg", "-stk", "CC BY-SA 4.0"),
        web("seed-13-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1b/Cisco_IP_Phone_7945_P1110470.jpg/960px-Cisco_IP_Phone_7945_P1110470.jpg", "-stk", "CC BY-SA 4.0"),
        web("seed-13-2", "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/51/Snom_D715_black.jpg/960px-Snom_D715_black.jpg", "Reinhard Müller", "CC BY-SA 4.0"),
    ),
    11 to listOf(
        web("seed-11-0", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/80/Anker_PowerConf_S500_Speakerphone.jpg/960px-Anker_PowerConf_S500_Speakerphone.jpg", "Shorn again", "CC BY-SA 4.0"),
        web("seed-11-1", "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/be/HuddleCamHD_Speaker_Phone.jpg/960px-HuddleCamHD_Speaker_Phone.jpg", "Mbrickn", "CC BY 4.0"),
    ),
)
