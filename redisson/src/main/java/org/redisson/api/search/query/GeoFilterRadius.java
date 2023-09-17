/**
 * Copyright (c) 2013-2022 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api.search.query;

import org.redisson.api.GeoUnit;

/**
 *
 *
 * @author Nikita Koksharov
 *
 */
public interface GeoFilterRadius {

    /**
     * Defines search within radius
     *
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return search conditions object
     */
    QueryFilter radius(double radius, GeoUnit geoUnit);

}
