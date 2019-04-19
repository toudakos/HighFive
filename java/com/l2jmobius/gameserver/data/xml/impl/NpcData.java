/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jmobius.gameserver.data.xml.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jmobius.Config;
import com.l2jmobius.commons.util.CommonUtil;
import com.l2jmobius.commons.util.IGameXmlReader;
import com.l2jmobius.gameserver.datatables.ItemTable;
import com.l2jmobius.gameserver.enums.AISkillScope;
import com.l2jmobius.gameserver.enums.DropType;
import com.l2jmobius.gameserver.model.StatsSet;
import com.l2jmobius.gameserver.model.actor.templates.L2NpcTemplate;
import com.l2jmobius.gameserver.model.base.ClassId;
import com.l2jmobius.gameserver.model.effects.L2EffectType;
import com.l2jmobius.gameserver.model.holders.DropHolder;
import com.l2jmobius.gameserver.model.holders.MinionHolder;
import com.l2jmobius.gameserver.model.holders.SkillHolder;
import com.l2jmobius.gameserver.model.skills.Skill;

/**
 * NPC data parser.
 * @author NosBit
 */
public class NpcData implements IGameXmlReader
{
	private final Map<Integer, L2NpcTemplate> _npcs = new HashMap<>();
	private final Map<String, Integer> _clans = new HashMap<>();
	private static final List<Integer> _masterMonsterIDs = new ArrayList<>();
	
	protected NpcData()
	{
		load();
	}
	
	@Override
	public synchronized void load()
	{
		_masterMonsterIDs.clear();
		
		parseDatapackDirectory("data/stats/npcs", false);
		LOGGER.info(getClass().getSimpleName() + ": Loaded " + _npcs.size() + " NPCs.");
		
		if (Config.CUSTOM_NPC_DATA)
		{
			final int npcCount = _npcs.size();
			parseDatapackDirectory("data/stats/npcs/custom", true);
			LOGGER.info(getClass().getSimpleName() + ": Loaded " + (_npcs.size() - npcCount) + " Custom NPCs.");
		}
		
		loadNpcsSkillLearn();
	}
	
	@Override
	public void parseDocument(Document doc, File f)
	{
		for (Node node = doc.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if ("list".equalsIgnoreCase(node.getNodeName()))
			{
				for (Node listNode = node.getFirstChild(); listNode != null; listNode = listNode.getNextSibling())
				{
					if ("npc".equalsIgnoreCase(listNode.getNodeName()))
					{
						NamedNodeMap attrs = listNode.getAttributes();
						final StatsSet set = new StatsSet(new HashMap<>());
						final int npcId = parseInteger(attrs, "id");
						Map<String, Object> parameters = null;
						Map<Integer, Skill> skills = null;
						Set<Integer> clans = null;
						Set<Integer> ignoreClanNpcIds = null;
						List<DropHolder> dropLists = null;
						set.set("id", npcId);
						set.set("displayId", parseInteger(attrs, "displayId"));
						set.set("level", parseByte(attrs, "level"));
						set.set("type", parseString(attrs, "type"));
						set.set("name", parseString(attrs, "name"));
						set.set("usingServerSideName", parseBoolean(attrs, "usingServerSideName"));
						set.set("title", parseString(attrs, "title"));
						set.set("usingServerSideTitle", parseBoolean(attrs, "usingServerSideTitle"));
						for (Node npcNode = listNode.getFirstChild(); npcNode != null; npcNode = npcNode.getNextSibling())
						{
							attrs = npcNode.getAttributes();
							switch (npcNode.getNodeName().toLowerCase())
							{
								case "parameters":
								{
									if (parameters == null)
									{
										parameters = new HashMap<>();
									}
									
									for (Node parametersNode = npcNode.getFirstChild(); parametersNode != null; parametersNode = parametersNode.getNextSibling())
									{
										attrs = parametersNode.getAttributes();
										switch (parametersNode.getNodeName().toLowerCase())
										{
											case "param":
											{
												parameters.put(parseString(attrs, "name"), parseString(attrs, "value"));
												break;
											}
											case "skill":
											{
												parameters.put(parseString(attrs, "name"), new SkillHolder(parseInteger(attrs, "id"), parseInteger(attrs, "level")));
												break;
											}
											case "minions":
											{
												final List<MinionHolder> minions = new ArrayList<>(1);
												for (Node minionsNode = parametersNode.getFirstChild(); minionsNode != null; minionsNode = minionsNode.getNextSibling())
												{
													if (minionsNode.getNodeName().equalsIgnoreCase("npc"))
													{
														attrs = minionsNode.getAttributes();
														minions.add(new MinionHolder(parseInteger(attrs, "id"), parseInteger(attrs, "count"), parseInteger(attrs, "respawnTime"), parseInteger(attrs, "weightPoint")));
													}
												}
												
												if (!minions.isEmpty())
												{
													parameters.put(parseString(parametersNode.getAttributes(), "name"), minions);
												}
												
												break;
											}
										}
									}
									break;
								}
								case "race":
								case "sex":
								{
									set.set(npcNode.getNodeName(), npcNode.getTextContent().toUpperCase());
									break;
								}
								case "equipment":
								{
									set.set("chestId", parseInteger(attrs, "chest"));
									set.set("rhandId", parseInteger(attrs, "rhand"));
									set.set("lhandId", parseInteger(attrs, "lhand"));
									set.set("weaponEnchant", parseInteger(attrs, "weaponEnchant"));
									break;
								}
								case "acquire":
								{
									set.set("exp", parseDouble(attrs, "exp"));
									set.set("sp", parseDouble(attrs, "sp"));
									set.set("raidPoints", parseDouble(attrs, "raidPoints"));
									break;
								}
								case "stats":
								{
									set.set("baseSTR", parseInteger(attrs, "str"));
									set.set("baseINT", parseInteger(attrs, "int"));
									set.set("baseDEX", parseInteger(attrs, "dex"));
									set.set("baseWIT", parseInteger(attrs, "wit"));
									set.set("baseCON", parseInteger(attrs, "con"));
									set.set("baseMEN", parseInteger(attrs, "men"));
									for (Node statsNode = npcNode.getFirstChild(); statsNode != null; statsNode = statsNode.getNextSibling())
									{
										attrs = statsNode.getAttributes();
										switch (statsNode.getNodeName().toLowerCase())
										{
											case "vitals":
											{
												set.set("baseHpMax", parseDouble(attrs, "hp"));
												set.set("baseHpReg", parseDouble(attrs, "hpRegen"));
												set.set("baseMpMax", parseDouble(attrs, "mp"));
												set.set("baseMpReg", parseDouble(attrs, "mpRegen"));
												break;
											}
											case "attack":
											{
												set.set("basePAtk", parseDouble(attrs, "physical"));
												set.set("baseMAtk", parseDouble(attrs, "magical"));
												set.set("baseRndDam", parseInteger(attrs, "random"));
												set.set("baseCritRate", parseInteger(attrs, "critical"));
												set.set("accuracy", parseDouble(attrs, "accuracy")); // TODO: Implement me
												set.set("basePAtkSpd", parseInteger(attrs, "attackSpeed"));
												set.set("reuseDelay", parseInteger(attrs, "reuseDelay")); // TODO: Implement me
												set.set("baseAtkType", parseString(attrs, "type"));
												set.set("baseAtkRange", parseInteger(attrs, "range"));
												set.set("distance", parseInteger(attrs, "distance")); // TODO: Implement me
												set.set("width", parseInteger(attrs, "width")); // TODO: Implement me
												break;
											}
											case "defence":
											{
												set.set("basePDef", parseDouble(attrs, "physical"));
												set.set("baseMDef", parseDouble(attrs, "magical"));
												set.set("evasion", parseInteger(attrs, "evasion")); // TODO: Implement me
												set.set("baseShldDef", parseInteger(attrs, "shield"));
												set.set("baseShldRate", parseInteger(attrs, "shieldRate"));
												break;
											}
											case "attribute":
											{
												for (Node attributeNode = statsNode.getFirstChild(); attributeNode != null; attributeNode = attributeNode.getNextSibling())
												{
													attrs = attributeNode.getAttributes();
													switch (attributeNode.getNodeName().toLowerCase())
													{
														case "attack":
														{
															final String attackAttributeType = parseString(attrs, "type");
															switch (attackAttributeType.toUpperCase())
															{
																case "FIRE":
																{
																	set.set("baseFire", parseInteger(attrs, "value"));
																	break;
																}
																case "WATER":
																{
																	set.set("baseWater", parseInteger(attrs, "value"));
																	break;
																}
																case "WIND":
																{
																	set.set("baseWind", parseInteger(attrs, "value"));
																	break;
																}
																case "EARTH":
																{
																	set.set("baseEarth", parseInteger(attrs, "value"));
																	break;
																}
																case "DARK":
																{
																	set.set("baseDark", parseInteger(attrs, "value"));
																	break;
																}
																case "HOLY":
																{
																	set.set("baseHoly", parseInteger(attrs, "value"));
																	break;
																}
															}
															break;
														}
														case "defence":
														{
															set.set("baseFireRes", parseInteger(attrs, "fire"));
															set.set("baseWaterRes", parseInteger(attrs, "water"));
															set.set("baseWindRes", parseInteger(attrs, "wind"));
															set.set("baseEarthRes", parseInteger(attrs, "earth"));
															set.set("baseHolyRes", parseInteger(attrs, "holy"));
															set.set("baseDarkRes", parseInteger(attrs, "dark"));
															set.set("baseElementRes", parseInteger(attrs, "default"));
															break;
														}
													}
												}
												break;
											}
											case "speed":
											{
												for (Node speedNode = statsNode.getFirstChild(); speedNode != null; speedNode = speedNode.getNextSibling())
												{
													attrs = speedNode.getAttributes();
													switch (speedNode.getNodeName().toLowerCase())
													{
														case "walk":
														{
															set.set("baseWalkSpd", parseDouble(attrs, "ground"));
															set.set("baseSwimWalkSpd", parseDouble(attrs, "swim"));
															set.set("baseFlyWalkSpd", parseDouble(attrs, "fly"));
															break;
														}
														case "run":
														{
															set.set("baseRunSpd", parseDouble(attrs, "ground"));
															set.set("baseSwimRunSpd", parseDouble(attrs, "swim"));
															set.set("baseFlyRunSpd", parseDouble(attrs, "fly"));
															break;
														}
													}
												}
												break;
											}
											case "hittime":
											{
												set.set("hitTime", npcNode.getTextContent()); // TODO: Implement me default 600 (value in ms)
												break;
											}
										}
									}
									break;
								}
								case "status":
								{
									set.set("unique", parseBoolean(attrs, "unique"));
									set.set("attackable", parseBoolean(attrs, "attackable"));
									set.set("targetable", parseBoolean(attrs, "targetable"));
									set.set("talkable", parseBoolean(attrs, "talkable"));
									set.set("undying", parseBoolean(attrs, "undying"));
									set.set("showName", parseBoolean(attrs, "showName"));
									set.set("randomWalk", parseBoolean(attrs, "randomWalk"));
									set.set("randomAnimation", parseBoolean(attrs, "randomAnimation"));
									set.set("flying", parseBoolean(attrs, "flying"));
									set.set("canMove", parseBoolean(attrs, "canMove"));
									set.set("noSleepMode", parseBoolean(attrs, "noSleepMode"));
									set.set("passableDoor", parseBoolean(attrs, "passableDoor"));
									set.set("hasSummoner", parseBoolean(attrs, "hasSummoner"));
									set.set("canBeSown", parseBoolean(attrs, "canBeSown"));
									set.set("fakePlayer", parseBoolean(attrs, "fakePlayer"));
									set.set("fakePlayerTalkable", parseBoolean(attrs, "fakePlayerTalkable"));
									break;
								}
								case "skilllist":
								{
									skills = new HashMap<>();
									for (Node skillListNode = npcNode.getFirstChild(); skillListNode != null; skillListNode = skillListNode.getNextSibling())
									{
										if ("skill".equalsIgnoreCase(skillListNode.getNodeName()))
										{
											attrs = skillListNode.getAttributes();
											final int skillId = parseInteger(attrs, "id");
											final int skillLevel = parseInteger(attrs, "level");
											final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
											if (skill != null)
											{
												skills.put(skill.getId(), skill);
											}
											else
											{
												LOGGER.warning("[" + f.getName() + "] skill not found. NPC ID: " + npcId + " Skill ID:" + skillId + " Skill Level: " + skillLevel);
											}
										}
									}
									break;
								}
								case "shots":
								{
									set.set("soulShot", parseInteger(attrs, "soul"));
									set.set("spiritShot", parseInteger(attrs, "spirit"));
									set.set("shotShotChance", parseInteger(attrs, "shotChance"));
									set.set("spiritShotChance", parseInteger(attrs, "spiritChance"));
									break;
								}
								case "corpsetime":
								{
									set.set("corpseTime", npcNode.getTextContent());
									break;
								}
								case "excrteffect":
								{
									set.set("exCrtEffect", npcNode.getTextContent()); // TODO: Implement me default ? type boolean
									break;
								}
								case "snpcprophprate":
								{
									set.set("sNpcPropHpRate", npcNode.getTextContent()); // TODO: Implement me default 1 type double
									break;
								}
								case "ai":
								{
									set.set("aiType", parseString(attrs, "type"));
									set.set("aggroRange", parseInteger(attrs, "aggroRange"));
									set.set("clanHelpRange", parseInteger(attrs, "clanHelpRange"));
									set.set("dodge", parseInteger(attrs, "dodge"));
									set.set("isChaos", parseBoolean(attrs, "isChaos"));
									set.set("isAggressive", parseBoolean(attrs, "isAggressive"));
									for (Node aiNode = npcNode.getFirstChild(); aiNode != null; aiNode = aiNode.getNextSibling())
									{
										attrs = aiNode.getAttributes();
										switch (aiNode.getNodeName().toLowerCase())
										{
											case "skill":
											{
												set.set("minSkillChance", parseInteger(attrs, "minChance"));
												set.set("maxSkillChance", parseInteger(attrs, "maxChance"));
												set.set("primarySkillId", parseInteger(attrs, "primaryId"));
												set.set("shortRangeSkillId", parseInteger(attrs, "shortRangeId"));
												set.set("shortRangeSkillChance", parseInteger(attrs, "shortRangeChance"));
												set.set("longRangeSkillId", parseInteger(attrs, "longRangeId"));
												set.set("longRangeSkillChance", parseInteger(attrs, "longRangeChance"));
												break;
											}
											case "clanlist":
											{
												for (Node clanListNode = aiNode.getFirstChild(); clanListNode != null; clanListNode = clanListNode.getNextSibling())
												{
													attrs = clanListNode.getAttributes();
													switch (clanListNode.getNodeName().toLowerCase())
													{
														case "clan":
														{
															if (clans == null)
															{
																clans = new HashSet<>(1);
															}
															clans.add(getOrCreateClanId(clanListNode.getTextContent()));
															break;
														}
														case "ignorenpcid":
														{
															if (ignoreClanNpcIds == null)
															{
																ignoreClanNpcIds = new HashSet<>(1);
															}
															ignoreClanNpcIds.add(Integer.parseInt(clanListNode.getTextContent()));
															break;
														}
													}
												}
												break;
											}
										}
									}
									break;
								}
								case "droplists":
								{
									for (Node drop_lists_node = npcNode.getFirstChild(); drop_lists_node != null; drop_lists_node = drop_lists_node.getNextSibling())
									{
										DropType dropType = null;
										
										try
										{
											dropType = Enum.valueOf(DropType.class, drop_lists_node.getNodeName().toUpperCase());
										}
										catch (Exception e)
										{
										}
										
										if (dropType != null)
										{
											if (dropLists == null)
											{
												dropLists = new ArrayList<>();
											}
											
											for (Node drop_node = drop_lists_node.getFirstChild(); drop_node != null; drop_node = drop_node.getNextSibling())
											{
												final NamedNodeMap drop_attrs = drop_node.getAttributes();
												if ("item".equals(drop_node.getNodeName().toLowerCase()))
												{
													final DropHolder dropItem = new DropHolder(dropType, parseInteger(drop_attrs, "id"), parseLong(drop_attrs, "min"), parseLong(drop_attrs, "max"), parseDouble(drop_attrs, "chance"));
													if (ItemTable.getInstance().getTemplate(parseInteger(drop_attrs, "id")) == null)
													{
														LOGGER.warning("DropListItem: Could not find item with id " + parseInteger(drop_attrs, "id") + ".");
													}
													else
													{
														dropLists.add(dropItem);
													}
												}
											}
										}
									}
									break;
								}
								case "collision":
								{
									for (Node collisionNode = npcNode.getFirstChild(); collisionNode != null; collisionNode = collisionNode.getNextSibling())
									{
										attrs = collisionNode.getAttributes();
										switch (collisionNode.getNodeName().toLowerCase())
										{
											case "radius":
											{
												set.set("collisionRadius", parseDouble(attrs, "normal"));
												set.set("collisionRadiusGrown", parseDouble(attrs, "grown"));
												break;
											}
											case "height":
											{
												set.set("collisionHeight", parseDouble(attrs, "normal"));
												set.set("collisionHeightGrown", parseDouble(attrs, "grown"));
												break;
											}
										}
									}
									break;
								}
							}
						}
						
						L2NpcTemplate template = _npcs.get(npcId);
						if (template == null)
						{
							template = new L2NpcTemplate(set);
							_npcs.put(template.getId(), template);
						}
						else
						{
							template.set(set);
						}
						
						template.setParameters(parameters != null ? new StatsSet(Collections.unmodifiableMap(parameters)) : StatsSet.EMPTY_STATSET);
						
						if (skills != null)
						{
							Map<AISkillScope, List<Skill>> aiSkillLists = null;
							for (Skill skill : skills.values())
							{
								if (skill.isPassive())
								{
									continue;
								}
								
								if (aiSkillLists == null)
								{
									aiSkillLists = new EnumMap<>(AISkillScope.class);
								}
								
								final List<AISkillScope> aiSkillScopes = new ArrayList<>();
								final AISkillScope shortOrLongRangeScope = skill.getCastRange() <= 150 ? AISkillScope.SHORT_RANGE : AISkillScope.LONG_RANGE;
								if (skill.isSuicideAttack())
								{
									aiSkillScopes.add(AISkillScope.SUICIDE);
								}
								else
								{
									aiSkillScopes.add(AISkillScope.GENERAL);
									
									if (skill.isContinuous())
									{
										if (!skill.isDebuff())
										{
											aiSkillScopes.add(AISkillScope.BUFF);
										}
										else
										{
											aiSkillScopes.add(AISkillScope.DEBUFF);
											aiSkillScopes.add(AISkillScope.COT);
											aiSkillScopes.add(shortOrLongRangeScope);
										}
									}
									else if (skill.hasEffectType(L2EffectType.DISPEL, L2EffectType.DISPEL_BY_SLOT))
									{
										aiSkillScopes.add(AISkillScope.NEGATIVE);
										aiSkillScopes.add(shortOrLongRangeScope);
									}
									else if (skill.hasEffectType(L2EffectType.HEAL))
									{
										aiSkillScopes.add(AISkillScope.HEAL);
									}
									else if (skill.hasEffectType(L2EffectType.PHYSICAL_ATTACK, L2EffectType.PHYSICAL_ATTACK_HP_LINK, L2EffectType.MAGICAL_ATTACK, L2EffectType.DEATH_LINK, L2EffectType.HP_DRAIN))
									{
										aiSkillScopes.add(AISkillScope.ATTACK);
										aiSkillScopes.add(AISkillScope.UNIVERSAL);
										aiSkillScopes.add(shortOrLongRangeScope);
									}
									else if (skill.hasEffectType(L2EffectType.SLEEP))
									{
										aiSkillScopes.add(AISkillScope.IMMOBILIZE);
									}
									else if (skill.hasEffectType(L2EffectType.STUN, L2EffectType.ROOT))
									{
										aiSkillScopes.add(AISkillScope.IMMOBILIZE);
										aiSkillScopes.add(shortOrLongRangeScope);
									}
									else if (skill.hasEffectType(L2EffectType.MUTE, L2EffectType.FEAR))
									{
										aiSkillScopes.add(AISkillScope.COT);
										aiSkillScopes.add(shortOrLongRangeScope);
									}
									else if (skill.hasEffectType(L2EffectType.PARALYZE))
									{
										aiSkillScopes.add(AISkillScope.IMMOBILIZE);
										aiSkillScopes.add(shortOrLongRangeScope);
									}
									else if (skill.hasEffectType(L2EffectType.DMG_OVER_TIME, L2EffectType.DMG_OVER_TIME_PERCENT))
									{
										aiSkillScopes.add(shortOrLongRangeScope);
									}
									else if (skill.hasEffectType(L2EffectType.RESURRECTION))
									{
										aiSkillScopes.add(AISkillScope.RES);
									}
									else
									{
										aiSkillScopes.add(AISkillScope.UNIVERSAL);
									}
								}
								
								for (AISkillScope aiSkillScope : aiSkillScopes)
								{
									List<Skill> aiSkills = aiSkillLists.get(aiSkillScope);
									if (aiSkills == null)
									{
										aiSkills = new ArrayList<>();
										aiSkillLists.put(aiSkillScope, aiSkills);
									}
									
									aiSkills.add(skill);
								}
							}
							
							template.setSkills(skills);
							template.setAISkillLists(aiSkillLists);
						}
						else
						{
							template.setSkills(null);
							template.setAISkillLists(null);
						}
						
						template.setClans(clans);
						template.setIgnoreClanNpcIds(ignoreClanNpcIds);
						
						if (dropLists != null)
						{
							for (DropHolder dropHolder : dropLists)
							{
								switch (dropHolder.getDropType())
								{
									case DROP:
									{
										template.addDrop(dropHolder);
										break;
									}
									case SPOIL:
									{
										template.addSpoil(dropHolder);
										break;
									}
								}
							}
						}
						
						if (!template.getParameters().getMinionList("Privates").isEmpty())
						{
							if (template.getParameters().getSet().get("SummonPrivateRate") == null)
							{
								_masterMonsterIDs.add(template.getId());
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Gets or creates a clan id if it doesnt exists.
	 * @param clanName the clan name to get or create its id
	 * @return the clan id for the given clan name
	 */
	private int getOrCreateClanId(String clanName)
	{
		Integer id = _clans.get(clanName);
		if (id == null)
		{
			id = _clans.size();
			_clans.put(clanName, id);
		}
		return id;
	}
	
	/**
	 * Gets the clan id
	 * @param clanName the clan name to get its id
	 * @return the clan id for the given clan name if it exists, -1 otherwise
	 */
	public int getClanId(String clanName)
	{
		final Integer id = _clans.get(clanName);
		return id != null ? id : -1;
	}
	
	/**
	 * Gets the template.
	 * @param id the template Id to get.
	 * @return the template for the given id.
	 */
	public L2NpcTemplate getTemplate(int id)
	{
		return _npcs.get(id);
	}
	
	/**
	 * Gets the template by name.
	 * @param name of the template to get.
	 * @return the template for the given name.
	 */
	public L2NpcTemplate getTemplateByName(String name)
	{
		for (L2NpcTemplate npcTemplate : _npcs.values())
		{
			if (npcTemplate.getName().equalsIgnoreCase(name))
			{
				return npcTemplate;
			}
		}
		return null;
	}
	
	/**
	 * Gets all templates matching the filter.
	 * @param filter
	 * @return the template list for the given filter
	 */
	public List<L2NpcTemplate> getTemplates(Predicate<L2NpcTemplate> filter)
	{
		//@formatter:off
			return _npcs.values().stream()
			.filter(filter)
			.collect(Collectors.toList());
		//@formatter:on
	}
	
	/**
	 * Gets the all of level.
	 * @param lvls of all the templates to get.
	 * @return the template list for the given level.
	 */
	public List<L2NpcTemplate> getAllOfLevel(int... lvls)
	{
		return getTemplates(template -> CommonUtil.contains(lvls, template.getLevel()));
	}
	
	/**
	 * Gets the all monsters of level.
	 * @param lvls of all the monster templates to get.
	 * @return the template list for the given level.
	 */
	public List<L2NpcTemplate> getAllMonstersOfLevel(int... lvls)
	{
		return getTemplates(template -> CommonUtil.contains(lvls, template.getLevel()) && template.isType("L2Monster"));
	}
	
	/**
	 * Gets the all npc starting with.
	 * @param text of all the NPC templates which its name start with.
	 * @return the template list for the given letter.
	 */
	public List<L2NpcTemplate> getAllNpcStartingWith(String text)
	{
		return getTemplates(template -> template.isType("L2Npc") && template.getName().startsWith(text));
	}
	
	/**
	 * Gets the all npc of class type.
	 * @param classTypes of all the templates to get.
	 * @return the template list for the given class type.
	 */
	public List<L2NpcTemplate> getAllNpcOfClassType(String... classTypes)
	{
		return getTemplates(template -> CommonUtil.contains(classTypes, template.getType(), true));
	}
	
	public void loadNpcsSkillLearn()
	{
		_npcs.values().forEach(template ->
		{
			final List<ClassId> teachInfo = SkillLearnData.getInstance().getSkillLearnData(template.getId());
			if (teachInfo != null)
			{
				template.addTeachInfo(teachInfo);
			}
		});
	}
	
	/**
	 * @return the IDs of monsters that have minions.
	 */
	public static List<Integer> getMasterMonsterIDs()
	{
		return _masterMonsterIDs;
	}
	
	/**
	 * Gets the single instance of NpcData.
	 * @return single instance of NpcData
	 */
	public static NpcData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final NpcData _instance = new NpcData();
	}
}