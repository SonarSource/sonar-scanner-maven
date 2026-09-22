/*
 * SonarQube Scanner for Maven :: Reactor Converter
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
/**
 * Converts a Maven reactor into the {@code sonar.*} analysis properties that describe it, using nothing but
 * Maven's own libraries. Shared between the Sonar Maven Plugin, which hands the properties to a scanner engine it
 * downloads, and the SonarQube scanner engine, which embeds Maven to read a reactor it was pointed at.
 */
@ParametersAreNonnullByDefault
package org.sonarsource.scanner.maven.converter;

import javax.annotation.ParametersAreNonnullByDefault;
