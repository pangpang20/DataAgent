/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.audaque.cloud.ai.dataagent.service.menu;

import com.audaque.cloud.ai.dataagent.dto.auth.MenuTreeDTO;
import com.audaque.cloud.ai.dataagent.dto.menu.MenuCreateRequest;
import com.audaque.cloud.ai.dataagent.dto.menu.MenuUpdateRequest;
import com.audaque.cloud.ai.dataagent.entity.SysMenu;
import com.audaque.cloud.ai.dataagent.exception.BizException;
import com.audaque.cloud.ai.dataagent.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

	private final SysMenuMapper sysMenuMapper;

	@Override
	public SysMenu createMenu(MenuCreateRequest request) {
		SysMenu menu = SysMenu.builder()
			.parentId(request.getParentId() != null ? request.getParentId() : 0L)
			.menuName(request.getMenuName())
			.menuType(request.getMenuType())
			.path(request.getPath())
			.component(request.getComponent())
			.icon(request.getIcon())
			.permission(request.getPermission())
			.sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
			.visible(request.getVisible() != null ? request.getVisible() : 1)
			.status(1)
			.createTime(LocalDateTime.now())
			.updateTime(LocalDateTime.now())
			.build();

		sysMenuMapper.insert(menu);
		return menu;
	}

	@Override
	public SysMenu updateMenu(MenuUpdateRequest request) {
		SysMenu menu = sysMenuMapper.findById(request.getId());
		if (menu == null) {
			throw new BizException(404003, "菜单不存在");
		}

		SysMenu update = SysMenu.builder()
			.id(request.getId())
			.parentId(request.getParentId())
			.menuName(request.getMenuName())
			.menuType(request.getMenuType())
			.path(request.getPath())
			.component(request.getComponent())
			.icon(request.getIcon())
			.permission(request.getPermission())
			.sortOrder(request.getSortOrder())
			.visible(request.getVisible())
			.status(request.getStatus())
			.updateTime(LocalDateTime.now())
			.build();

		sysMenuMapper.updateById(update);
		return sysMenuMapper.findById(request.getId());
	}

	@Override
	public void deleteMenu(Long id) {
		SysMenu menu = sysMenuMapper.findById(id);
		if (menu == null) {
			throw new BizException(404003, "菜单不存在");
		}

		Long childCount = sysMenuMapper.countByParentId(id);
		if (childCount > 0) {
			throw new BizException(400013, "该菜单下有" + childCount + "个子菜单，不能删除");
		}

		sysMenuMapper.deleteById(id);
	}

	@Override
	public SysMenu getMenuById(Long id) {
		SysMenu menu = sysMenuMapper.findById(id);
		if (menu == null) {
			throw new BizException(404003, "菜单不存在");
		}
		return menu;
	}

	@Override
	public List<MenuTreeDTO> listMenuTree() {
		List<SysMenu> menus = sysMenuMapper.findAll();
		return buildMenuTree(menus, 0L);
	}

	@Override
	public List<SysMenu> listAllMenus() {
		return sysMenuMapper.findAll();
	}

	private List<MenuTreeDTO> buildMenuTree(List<SysMenu> menus, Long parentId) {
		return menus.stream()
			.filter(m -> parentId.equals(m.getParentId()))
			.map(m -> MenuTreeDTO.builder()
				.id(m.getId())
				.parentId(m.getParentId())
				.menuName(m.getMenuName())
				.menuType(m.getMenuType())
				.path(m.getPath())
				.component(m.getComponent())
				.icon(m.getIcon())
				.permission(m.getPermission())
				.sortOrder(m.getSortOrder())
				.visible(m.getVisible())
				.children(buildMenuTree(menus, m.getId()))
				.build())
			.collect(Collectors.toList());
	}

}
