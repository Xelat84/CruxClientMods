package com.wurmonline.clientmods.auctionhouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the server's {@code AuctionCategoryIds} / {@code AuctionCategoryTree}. The ids are a
 * wire contract: snapshot rows carry a {@code categoryId} the client matches against this tree to
 * filter the Browse list locally. Keep this in lockstep with the C# taxonomy.
 */
public final class AuctionCategory {

	public static final int ALL = 0;

	public static final int TOOLS = 100;
	public static final int WEAPONS = 200;
	public static final int ARMOUR = 300;

	public static final int RESOURCES = 400;
	public static final int RESOURCES_WOOD = 401;
	public static final int RESOURCES_ORE_METAL = 402;
	public static final int RESOURCES_CLOTH_FIBRE = 403;
	public static final int RESOURCES_STONE_CLAY = 404;

	public static final int FOOD_FARM = 500;
	public static final int CONTAINERS = 600;
	public static final int OTHER = 900;

	public final int id;
	public final int parentId;
	public final String displayName;
	public final boolean leaf;

	private AuctionCategory(int id, int parentId, String displayName, boolean leaf) {
		this.id = id;
		this.parentId = parentId;
		this.displayName = displayName;
		this.leaf = leaf;
	}

	private static final List<AuctionCategory> TREE = new ArrayList<>();

	static {
		TREE.add(new AuctionCategory(TOOLS, ALL, "Tools", true));
		TREE.add(new AuctionCategory(WEAPONS, ALL, "Weapons", true));
		TREE.add(new AuctionCategory(ARMOUR, ALL, "Armour", true));
		TREE.add(new AuctionCategory(RESOURCES, ALL, "Resources", false));
		TREE.add(new AuctionCategory(RESOURCES_WOOD, RESOURCES, "Wood", true));
		TREE.add(new AuctionCategory(RESOURCES_ORE_METAL, RESOURCES, "Ore & metal", true));
		TREE.add(new AuctionCategory(RESOURCES_CLOTH_FIBRE, RESOURCES, "Cloth & fibre", true));
		TREE.add(new AuctionCategory(RESOURCES_STONE_CLAY, RESOURCES, "Stone & clay", true));
		TREE.add(new AuctionCategory(FOOD_FARM, ALL, "Food & farm", true));
		TREE.add(new AuctionCategory(CONTAINERS, ALL, "Containers", true));
		TREE.add(new AuctionCategory(OTHER, ALL, "Other", true));
	}

	public static List<AuctionCategory> all() {
		return TREE;
	}

	/** Human-readable "Parent > Child" label for a category id (for the buy-order item picker). */
	public static String pathOf(int id) {
		for (AuctionCategory c : TREE) {
			if (c.id == id) {
				if (c.parentId != ALL) {
					for (AuctionCategory p : TREE) {
						if (p.id == c.parentId) {
							return p.displayName + " > " + c.displayName;
						}
					}
				}
				return c.displayName;
			}
		}
		return "";
	}

	/** Roots = direct children of ALL (both leaf roots and parent roots). */
	public static List<AuctionCategory> roots() {
		List<AuctionCategory> out = new ArrayList<>();
		for (AuctionCategory c : TREE) {
			if (c.parentId == ALL) {
				out.add(c);
			}
		}
		return out;
	}

	public static List<AuctionCategory> childrenOf(int parentId) {
		List<AuctionCategory> out = new ArrayList<>();
		for (AuctionCategory c : TREE) {
			if (c.parentId == parentId) {
				out.add(c);
			}
		}
		return out;
	}

	public static boolean hasChildren(int id) {
		for (AuctionCategory c : TREE) {
			if (c.parentId == id) {
				return true;
			}
		}
		return false;
	}

	/** True if {@code categoryId} is {@code selected} itself or a descendant of it. */
	public static boolean matches(int selected, int categoryId) {
		if (selected == ALL || selected == categoryId) {
			return true;
		}
		int cur = categoryId;
		while (cur != ALL) {
			AuctionCategory c = find(cur);
			if (c == null) {
				return false;
			}
			if (c.parentId == selected) {
				return true;
			}
			cur = c.parentId;
		}
		return false;
	}

	private static AuctionCategory find(int id) {
		for (AuctionCategory c : TREE) {
			if (c.id == id) {
				return c;
			}
		}
		return null;
	}
}
