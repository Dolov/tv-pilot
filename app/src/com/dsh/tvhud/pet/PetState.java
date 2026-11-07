package com.dsh.tvhud.pet;

/**
 * 电子宠物的纯数据模型：只有状态，没有 Android IO，方便 {@link PetEngine} 单测。
 *
 * 数值口径见 docs/pet-design.md：
 * - EXP 是唯一成长值，Lv = EXP / 10，Lv 3/10/24/45 分别进化；
 * - 体重、牙齿只影响外观/表情，不参与「健康」判定，也不做好坏评价；
 * - 分支（Lv24 锁定）、变异（Lv45 后每周检定）、退化（只回退外观 2 天）都是纯外观层，
 *   不删除已购物品、不永久降阶。
 */
public final class PetState {
    /** 存档 schema：字段增删时 +1，旧存档按默认值补。v2 增加分支/变异/退化/商店/装饰。 */
    public static final int SCHEMA = 2;

    // 进化阶段
    public static final int STAGE_EGG = 0;
    public static final int STAGE_BABY = 1;
    public static final int STAGE_TEEN = 2;
    public static final int STAGE_ADULT = 3;
    public static final int STAGE_FINAL = 4;

    /** 各阶段累计 EXP 门槛（与 Lv 3/10/24/45 对齐）。 */
    public static final int[] STAGE_EXP = {0, 30, 100, 240, 450};
    public static final int[] STAGE_LEVEL = {1, 3, 10, 24, 45};

    // 分支进化：Lv24 按最近偏好锁定，只改外观；30 星可换线
    public static final int BRANCH_NONE = 0;
    public static final int BRANCH_WISDOM = 1;   // 智慧线
    public static final int BRANCH_SWIFT = 2;    // 疾风线
    public static final int BRANCH_GOURMET = 3;  // 美食线

    /** 退化时外观回退的时长：2 天，达标次日立刻恢复。 */
    public static final long DEGRADE_MS = 2L * 86400000L;

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
    /** 主动休息次数（分支：疾风线偏好）。 */
    public int totalRest;
    /** 吃饭次数（分支：美食线偏好）。 */
    public int totalEat;
    /** 累计赚到的星星（图鉴/报告用，不消费）。 */
    public int starsEarned;
    public int streakDays;

    /** 连续不达标天数（3 天触发一次外观退化）。 */
    public int badDays;
    /** 连续没进宠物房间的天数（>=2 时躲进壳里，一次互动恢复）。 */
    public int careMissDays;

    /** 退化：外观回退截止时间；0 表示没有退化。 */
    public long degradeUntil;
    /** 退化时显示成上一阶段（阶段号）。 */
    public int degradeStage = PetState.STAGE_EGG;

    /** 分支（Lv24 锁定）；NONE 表示还没到分叉点。 */
    public int branch = BRANCH_NONE;

    /** 变异：两个部位 + 配色；0 = 无变异。部位编号见 Shop / PetView。 */
    public int mutationPartA;
    public int mutationPartB;
    public int mutationPalette;
    public int mutationCount;
    /** 已见过的变异组合，逗号分隔（图鉴）。 */
    public String mutationsSeen = "";
    /** 上次变异检定的墙钟毫秒。 */
    public long lastMutationCheck;
    /** 有新变异/分支待庆祝：进宠物房间时播一次动画。 */
    public boolean celebrateEvolution;

    /** 已拥有的物品 id，逗号分隔（帽子/衣服/翅膀/装饰）。 */
    public String owned = "";
    /** 已摆放的房间装饰（槽位名，最多 3 个，逗号分隔）。 */
    public String decor = "";

    /** 上次结算（衰减）的墙钟毫秒；0 表示还没结算过。 */
    public long lastSettleMs;
    /** 上次进入宠物房间的墙钟毫秒。 */
    public long lastSeenMs;
    /** 换牙事件结束时间；0 表示没有换牙。 */
    public long teethingUntil;
    /** 是否已经经历过一次换牙（避免反复触发）。 */
    public boolean teethingDone;

    // 装备槽（空串 = 不穿；槽位值见 Shop）
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
        return stageOfExp(exp);
    }

    /** 退化期间显示上一阶段；只影响外观，EXP 不降阶段。 */
    public int displayStage(long now) {
        if (isDegraded(now) && degradeStage >= 0 && degradeStage < stage()) {
            return degradeStage;
        }
        return stage();
    }

    public boolean isDegraded(long now) {
        return degradeUntil > now;
    }

    /** 连续两天以上完全没进宠物房间：躲进壳里/变淡。一次互动即恢复。 */
    public boolean isShell() {
        return careMissDays >= 2;
    }

    /** 图鉴：记录见过的变异组合（部位按大小排序，避免 1-2 和 2-1 重复）。 */
    public void mutationSeenAdd(int a, int b) {
        String key = a < b ? (a + "-" + b) : (b + "-" + a);
        if (!csvHas(mutationsSeen, key)) {
            mutationsSeen = csvAdd(mutationsSeen, key);
        }
    }

    public int mutationSeenCount() {
        if (mutationsSeen == null || mutationsSeen.isEmpty()) {
            return 0;
        }
        return mutationsSeen.split(",").length;
    }

    public String stageName() {
        return stageName(stage());
    }

    public String displayName() {
        String base = stageName();
        if (mutationCount > 0 && stage() >= STAGE_FINAL) {
            return "变异·" + base;
        }
        if (branch != BRANCH_NONE && stage() >= STAGE_ADULT) {
            return branchName() + "·" + base;
        }
        return base;
    }

    public String branchName() {
        switch (branch) {
            case BRANCH_WISDOM:
                return "智慧";
            case BRANCH_SWIFT:
                return "疾风";
            case BRANCH_GOURMET:
                return "美食";
            default:
                return "";
        }
    }

    public String mutationLabel() {
        if (mutationCount <= 0) {
            return "";
        }
        return "变异 " + mutationPartName(mutationPartA) + " + "
                + mutationPartName(mutationPartB);
    }

    public static String mutationPartName(int part) {
        switch (part) {
            case 1:
                return "小角";
            case 2:
                return "星光";
            case 3:
                return "花纹";
            case 4:
                return "星尾";
            case 5:
                return "小虎牙";
            case 6:
                return "肩翅膀";
            default:
                return "无";
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

    // ---------- 物品 ----------

    public boolean owns(String itemId) {
        return itemId != null && !itemId.isEmpty() && csvHas(owned, itemId);
    }

    public void addOwned(String itemId) {
        if (itemId == null || itemId.isEmpty() || csvHas(owned, itemId)) {
            return;
        }
        owned = csvAdd(owned, itemId);
    }

    public boolean removeOwned(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        String next = csvRemove(owned, itemId);
        if (next.equals(owned)) {
            return false;
        }
        owned = next;
        return true;
    }

    public String[] decorList() {
        if (decor == null || decor.isEmpty()) {
            return new String[0];
        }
        return decor.split(",");
    }

    public boolean decorHas(String slot) {
        return slot != null && !slot.isEmpty() && csvHas(decor, slot);
    }

    // P0 的按等级解锁判断保留给旧调用方；新 UI 用 Shop.checkBuy 判断购买资格。
    public boolean canWearHat() {
        return level() >= 3;
    }

    public boolean canWearClothes() {
        return level() >= 10;
    }

    public boolean canWearWings() {
        return level() >= 24;
    }

    // ---------- 静态工具（纯函数，给 PetEngine/Shop 复用） ----------

    public static int stageOfExp(int exp) {
        int stage = STAGE_EGG;
        for (int i = 1; i < STAGE_EXP.length; i++) {
            if (exp >= STAGE_EXP[i]) {
                stage = i;
            }
        }
        return stage;
    }

    public static String stageName(int stage) {
        switch (stage) {
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

    public static boolean csvHas(String csv, String v) {
        if (csv == null || csv.isEmpty() || v == null || v.isEmpty()) {
            return false;
        }
        int from = 0;
        while (from <= csv.length()) {
            int at = csv.indexOf(',', from);
            String part = at < 0 ? csv.substring(from) : csv.substring(from, at);
            if (part.equals(v)) {
                return true;
            }
            if (at < 0) {
                break;
            }
            from = at + 1;
        }
        return false;
    }

    public static String csvAdd(String csv, String v) {
        if (csv == null || csv.isEmpty()) {
            return v;
        }
        return csv + "," + v;
    }

    public static String csvRemove(String csv, String v) {
        if (csv == null || csv.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int from = 0;
        while (from <= csv.length()) {
            int at = csv.indexOf(',', from);
            String part = at < 0 ? csv.substring(from) : csv.substring(from, at);
            if (!part.equals(v) && !part.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(part);
            }
            if (at < 0) {
                break;
            }
            from = at + 1;
        }
        return sb.toString();
    }
}
