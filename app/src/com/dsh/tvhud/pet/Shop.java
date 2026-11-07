package com.dsh.tvhud.pet;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店与衣柜的纯逻辑：物品目录、买/退/穿/喂。
 *
 * 红线（docs/pet-design.md §5.2）：只有星星一种货币，成长值不能买；不做抽卡/限时/付费；
 * 买错可原价退。所有方法都不 import Android，方便 tests/java 离线覆盖。
 */
public final class Shop {
    public static final String TYPE_HAT = "hat";
    public static final String TYPE_CLOTHES = "clothes";
    public static final String TYPE_WINGS = "wings";
    public static final String TYPE_DECOR = "decor";
    public static final String TYPE_FOOD = "food";

    public static final int BRANCH_SWITCH_COST = 30;
    public static final int MAX_DECOR = 3;

    // 返回码：UI 根据码给不同的孩子话术，不显示错误码
    public static final int OK = 0;
    public static final int ERR_NOT_ENOUGH = 1;
    public static final int ERR_OWNED = 2;
    public static final int ERR_LEVEL = 3;
    public static final int ERR_UNKNOWN = 4;
    public static final int ERR_DECOR_FULL = 5;

    public static final class Item {
        public final String id;
        public final String type;
        public final String name;
        public final int price;
        public final int unlockLevel;
        /** 穿戴槽位值（帽子=cap…；装饰=plant…；食物=kibble…）。 */
        public final String slot;
        public final String desc;

        Item(String id, String type, String name, int price, int unlockLevel,
             String slot, String desc) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.price = price;
            this.unlockLevel = unlockLevel;
            this.slot = slot;
            this.desc = desc;
        }

        public boolean durable() {
            return !TYPE_FOOD.equals(type);
        }
    }

    private static final Item[] CATALOG = {
            // 食物：立即喂，不进衣柜
            new Item("food_kibble", TYPE_FOOD, "狗粮", 5, 0, "kibble", "饱饱的，长一点点肉"),
            new Item("food_snack", TYPE_FOOD, "小零食", 8, 0, "snack", "开心一下"),
            new Item("food_candy", TYPE_FOOD, "甜食", 5, 0, "candy", "超开心，但牙齿要刷"),
            // 帽子
            new Item("hat_cap", TYPE_HAT, "小鸭舌帽", 10, 3, "cap", "红红的，跑起来不挡眼"),
            new Item("hat_beanie", TYPE_HAT, "毛线帽", 15, 3, "beanie", "软软的，冬天戴"),
            new Item("hat_crown", TYPE_HAT, "星星王冠", 25, 10, "crown", "亮闪闪，像小王子"),
            new Item("hat_chef", TYPE_HAT, "厨师帽", 25, 24, "chef", "做饭最好看"),
            // 衣服
            new Item("clothes_shirt", TYPE_CLOTHES, "湖蓝 T 恤", 20, 10, "shirt", "清清爽爽"),
            new Item("clothes_cape", TYPE_CLOTHES, "珊瑚斗篷", 30, 10, "cape", "会飘起来"),
            new Item("clothes_overalls", TYPE_CLOTHES, "牛仔背带裤", 25, 15, "overalls", "口袋多"),
            new Item("clothes_star", TYPE_CLOTHES, "星空背心", 40, 24, "star", "身上有小星星"),
            // 翅膀
            new Item("wings_small", TYPE_WINGS, "小翅膀", 50, 24, "small", "刚长出来的小翅膀"),
            new Item("wings_big", TYPE_WINGS, "大翅膀", 80, 45, "big", "可以飞过高楼"),
            new Item("wings_butterfly", TYPE_WINGS, "蝴蝶翅膀", 80, 45, "butterfly", "五颜六色"),
            // 房间装饰（最多同时摆 3 个）
            new Item("decor_plant", TYPE_DECOR, "小盆栽", 15, 0, "plant", "给房间一点绿"),
            new Item("decor_lamp", TYPE_DECOR, "星星灯", 20, 0, "lamp", "晚上会亮"),
            new Item("decor_poster", TYPE_DECOR, "电视海报", 25, 0, "poster", "宠物最喜欢的节目"),
            new Item("decor_rug", TYPE_DECOR, "圆地毯", 30, 0, "rug", "踩上去软软的"),
            new Item("decor_fish", TYPE_DECOR, "小鱼缸", 35, 0, "fish", "两条小鱼游啊游"),
            new Item("decor_balloon", TYPE_DECOR, "气球", 12, 0, "balloon", "飘在天花板上"),
    };

    private static final List<Item> HATS = filter(TYPE_HAT);
    private static final List<Item> CLOTHES = filter(TYPE_CLOTHES);
    private static final List<Item> WINGS = filter(TYPE_WINGS);
    private static final List<Item> DECOR = filter(TYPE_DECOR);
    private static final List<Item> FOODS = filter(TYPE_FOOD);

    private Shop() {
    }

    public static Item byId(String id) {
        if (id == null) {
            return null;
        }
        for (Item it : CATALOG) {
            if (it.id.equals(id)) {
                return it;
            }
        }
        return null;
    }

    public static List<Item> all() {
        List<Item> out = new ArrayList<Item>();
        for (Item it : CATALOG) {
            out.add(it);
        }
        return out;
    }

    public static List<Item> ofType(String type) {
        if (TYPE_HAT.equals(type)) {
            return HATS;
        }
        if (TYPE_CLOTHES.equals(type)) {
            return CLOTHES;
        }
        if (TYPE_WINGS.equals(type)) {
            return WINGS;
        }
        if (TYPE_DECOR.equals(type)) {
            return DECOR;
        }
        if (TYPE_FOOD.equals(type)) {
            return FOODS;
        }
        return new ArrayList<Item>();
    }

    private static List<Item> filter(String type) {
        List<Item> out = new ArrayList<Item>();
        for (Item it : CATALOG) {
            if (it.type.equals(type)) {
                out.add(it);
            }
        }
        return out;
    }

    /** 只检查不扣钱：UI 用来决定卡片上是「买」「已拥有」还是「Lv 不够」。 */
    public static int checkBuy(PetState s, String itemId) {
        Item it = byId(itemId);
        if (it == null || !it.durable()) {
            return ERR_UNKNOWN;
        }
        if (s.owns(it.id)) {
            return ERR_OWNED;
        }
        if (s.level() < it.unlockLevel) {
            return ERR_LEVEL;
        }
        if (s.stars < it.price) {
            return ERR_NOT_ENOUGH;
        }
        return OK;
    }

    /** 买一件耐用品：扣星、加入衣柜；装饰买完自动摆上（有空位时）。 */
    public static int buy(PetState s, String itemId) {
        int check = checkBuy(s, itemId);
        if (check != OK) {
            return check;
        }
        Item it = byId(itemId);
        s.stars -= it.price;
        s.addOwned(it.id);
        if (TYPE_DECOR.equals(it.type)) {
            placeDecor(s, it.slot, true);
        }
        return OK;
    }

    /** 原价退（买错后悔药）；已穿着的先脱下，装饰先收起来。 */
    public static boolean sell(PetState s, String itemId) {
        Item it = byId(itemId);
        if (it == null || !it.durable() || !s.owns(it.id)) {
            return false;
        }
        unequip(s, it);
        s.stars += it.price;
        s.removeOwned(it.id);
        return true;
    }

    /** 穿上/切换；装饰则是「摆上/收起」。 */
    public static boolean equip(PetState s, String itemId) {
        Item it = byId(itemId);
        if (it == null || !it.durable() || !s.owns(it.id)) {
            return false;
        }
        if (s.level() < it.unlockLevel) {
            return false;
        }
        if (TYPE_HAT.equals(it.type)) {
            s.hat = it.slot;
        } else if (TYPE_CLOTHES.equals(it.type)) {
            s.clothes = it.slot;
        } else if (TYPE_WINGS.equals(it.type)) {
            s.wings = it.slot;
        } else if (TYPE_DECOR.equals(it.type)) {
            placeDecor(s, it.slot, !s.decorHas(it.slot));
        } else {
            return false;
        }
        return true;
    }

    /** 脱掉某一类（传 Shop.TYPE_*；食物忽略）。 */
    public static boolean unequip(PetState s, String type) {
        if (TYPE_HAT.equals(type)) {
            s.hat = "";
            return true;
        }
        if (TYPE_CLOTHES.equals(type)) {
            s.clothes = "";
            return true;
        }
        if (TYPE_WINGS.equals(type)) {
            s.wings = "";
            return true;
        }
        return false;
    }

    private static void unequip(PetState s, Item it) {
        if (TYPE_HAT.equals(it.type) && it.slot.equals(s.hat)) {
            s.hat = "";
        } else if (TYPE_CLOTHES.equals(it.type) && it.slot.equals(s.clothes)) {
            s.clothes = "";
        } else if (TYPE_WINGS.equals(it.type) && it.slot.equals(s.wings)) {
            s.wings = "";
        } else if (TYPE_DECOR.equals(it.type)) {
            s.decor = PetState.csvRemove(s.decor, it.slot);
        }
    }

    /**
     * 摆放/收起装饰。最多同时 3 个：满了以后从最早摆的开始换，
     * 不让孩子因为点错而「装饰消失了」——物品仍在衣柜里。
     */
    public static boolean placeDecor(PetState s, String slot, boolean place) {
        if (slot == null || slot.isEmpty()) {
            return false;
        }
        if (!place) {
            s.decor = PetState.csvRemove(s.decor, slot);
            return true;
        }
        if (s.decorHas(slot)) {
            return true;
        }
        String[] cur = s.decorList();
        if (cur.length >= MAX_DECOR) {
            s.decor = PetState.csvRemove(s.decor, cur[0]);
        }
        s.decor = PetState.csvAdd(s.decor, slot);
        return true;
    }

    public static boolean isEquipped(PetState s, String itemId) {
        Item it = byId(itemId);
        if (it == null) {
            return false;
        }
        if (TYPE_HAT.equals(it.type)) {
            return it.slot.equals(s.hat);
        }
        if (TYPE_CLOTHES.equals(it.type)) {
            return it.slot.equals(s.clothes);
        }
        if (TYPE_WINGS.equals(it.type)) {
            return it.slot.equals(s.wings);
        }
        if (TYPE_DECOR.equals(it.type)) {
            return s.decorHas(it.slot);
        }
        return false;
    }

    /** 喂食：扣星 + 交给 PetEngine 应用状态变化。 */
    public static int feed(PetState s, String foodId, long now) {
        Item it = byId(foodId);
        if (it == null || !TYPE_FOOD.equals(it.type)) {
            return ERR_UNKNOWN;
        }
        if (s.stars < it.price) {
            return ERR_NOT_ENOUGH;
        }
        s.stars -= it.price;
        PetEngine.feed(s, it.slot, now);
        return OK;
    }

    public static String itemName(String id) {
        Item it = byId(id);
        return it == null ? "" : it.name;
    }

    /** 当前穿着的物品 id（没有就空串），给衣柜高亮用。 */
    public static String equippedId(PetState s, String type) {
        for (Item it : ofType(type)) {
            if (isEquipped(s, it.id)) {
                return it.id;
            }
        }
        return "";
    }
}
