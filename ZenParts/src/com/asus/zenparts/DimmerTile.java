/*
* Copyright (C) 2018 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.asus.zenparts;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class DimmerTile extends TileService {

    @Override
    public void onStartListening() {

        final boolean enabled = BacklightDimmer.isCurrentlyEnabled(this);

        Tile tile = getQsTile();
        if (enabled) {
            tile.setState(Tile.STATE_ACTIVE);
        } else {
            tile.setState(Tile.STATE_INACTIVE);
        }

        tile.updateTile();

        super.onStartListening();
    }

    @Override
    public void onClick() {
        Tile tile = getQsTile();
        if (BacklightDimmer.isCurrentlyEnabled(this)) {
            FileUtils.setValue(BacklightDimmer.getFile(), "N");
            tile.setState(Tile.STATE_INACTIVE);
        } else {
            FileUtils.setValue(BacklightDimmer.getFile(), "Y");
            tile.setState(Tile.STATE_ACTIVE);
        }
        tile.updateTile();
        super.onClick();
    }
}
