package com.dsh.tvhud.pet;

/**
 * 电子宠物的纯数据模型：只有状态，没有 Android IO，方便 {@link PetEngine} 单测。
 *
 * 数值口径见 docs/pet-design.md：
 * - EXP 是唯一成长值，Lv = EXP / 10，Lv 3/10/24/45 分别进化；
 * - 体重、牙齿只影响外观/表情，不参与「健康」判定，也不做好坏评价。
 */
public final class PetState {
    /** 存档 schema：字段增删时 +1，旧存档按默认值补。 */
    public static final int SCHEMA = 1;

    // 进化阶段
    public static final int STAGE_EGG = 0;
    public static final int STAGE_BABY = 1;
    public static final int STAGE_TEEN = 2;
    public static final int STAGE_ADULT = 3;
    public static final int STAGE_FINAL = 4;

    /** 各阶段累计 EXP 门槛（与 Lv 3/10/24/45 对齐）。 */
    public static final int[] STAGE_EXP = {0, 30, 100, 240, 450};
    public static final int[] STAGE_LEVEL = {1, 3, 10, 24, 45};

    // 每日照顾位（bit mask）
    public static final int CARE_EAT = 1;
    public static final int CARE_DRINK = 1 << 1;
    public static final int CARE_PET = 1 << 2;
    public static final int CARE_BATH = 1 << 3;
    public static final int CARE_BRUSH = 1 << 4;
    public static final int CARE_ALL = CARE_EAT | CARE_DRINK | CARE_PET | CARE_BATH | CARE_BRUSH;

    public int schema = SCHEMA;
    /** 每日计数所属的本地日 0 点（墙钟毫秒）。 */
    public long dayStart;
    /** 累计成长值；Lv 与阶段都由它派生。 */
    public int exp;
    /** 星星，唯一可消费货币。 */
    public int stars;

    // 0..100 的需求值
    public int hunger = 70;
    public int thirst = 70;
    public int mood = 70;
    public int energy = 80;
    /** 0..100：<35 轻飘飘、>70 圆滚滚，只影响外观。 */
    public int weight = 50;
    /** 0..100：牙齿亮白度，只影响表情/嘴巴。 */
    public int teeth = 80;

    /** 今日已完成照顾位。 */
    public int careMask;
    public int careTodayCount;
    public int quizToday;
    public int quizCorrect;
    public int totalCare;
    public int totalQuizCorrect;
    public int streakDays;

    /** 上次结算（衰减）的墙钟毫秒；0 表示还没结算过。 */
    public long lastSettleMs;
    /** 上次进入宠物房间的墙钟毫秒。 */
    public long lastSeenMs;
    /** 换牙事件结束时间；0 表示没有换牙。 */
    public long teethingUntil;
    /** 是否已经经历过一次换牙（避免反复触发）。 */
    public boolean teethingDone;

    // 装备槽（空串 = 不穿）
    public String hat = "";
    public String clothes = "";
    public String wings = "";

    /** 每日计数切到新的一天；不改变累计值。 */
    public void rollDay(long todayStart) {
        if (dayStart == todayStart) {
            return;
        }
        dayStart = todayStart;
        careMask = 0;
        careTodayCount = 0;
        quizToday = 0;
        quizCorrect = 0;
    }

    public int level() {
        return Math.max(1, exp / 10);
    }

    public int stage() {
        int stage = STAGE_EGG;
        for (int i = 1; i < STAGE_EXP.length; i++) {
            if (exp >= STAGE_EXP[i]) {
                stage = i;
            }
        }
        return stage;
    }

    public String stageName() {
        switch (stage()) {
            case STAGE_BABY:
                return "奶团兽";
            case STAGE_TEEN:
                return "闪电兽";
            case STAGE_ADULT:
                return "电视兽";
            case STAGE_FINAL:
                return "星翼兽";
            default:
                return "蛋";
        }
    }

    public String weightLabel() {
        if (weight < 35) {
            return "轻飘飘";
        }
        if (weight > 70) {
            return "圆滚滚";
        }
        return "标准";
    }

    public String teethLabel() {
        if (teeth < 30) {
            return "该刷牙了";
        }
        if (teeth < 60) {
            return "有点黄";
        }
        return "亮晶晶";
    }

    public String moodLabel() {
        if (mood < 30) {
            return "蔫蔫的";
        }
        if (mood < 60) {
            return "一般";
        }
        return "开心";
    }

    public boolean isTeething() {
        return teethingUntil > System.currentTimeMillis();
    }

    /** 给 UI 判断某件装备是否已解锁（P0 用等级，P1 再换商店）。 */
    public boolean canWearHat() {
        return level() >= 3;
    }

    public boolean canWearClothes() {
        return level() >= 10;
    }

    public boolean canWearWings() {
        return level() >= 24;
    }
}
