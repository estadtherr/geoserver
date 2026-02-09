/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

/* Copyright (c) 2017 Boundless - http://boundlessgeo.com All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.gsr.model.feature;

import static org.geoserver.gsr.GSRConfig.CURRENT_VERSION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.gsr.model.AbstractGSRModel;
import org.geoserver.gsr.model.GSRModel;
import org.geoserver.gsr.model.geometry.GeometryTypeEnum;
import org.geoserver.gsr.model.map.LayerEntry;
import org.geoserver.wfs.WFSInfo;
import org.geotools.api.feature.type.GeometryDescriptor;

/** Detailed model of a FeatureService */
public class FeatureServiceRoot extends AbstractGSRModel implements GSRModel {

    public final Double currentVersion = CURRENT_VERSION;
    public final String serviceDescription;
    public final String supportedQueryFormats = "JSON";
    public final String initialExtent = null;
    public final String fullExtent = null;

    private String workspace;

    public final List<LayerEntry> layers = new ArrayList<>();
    public final List<LayerEntry> tables = new ArrayList<>();

    public FeatureServiceRoot(WFSInfo service, String workspace, List<LayerInfo> layers) {
        this.workspace = workspace;
        serviceDescription = service.getTitle() != null ? service.getTitle() : service.getName();
        for (int i = 0; i < layers.size(); i++) {
            LayerInfo l = layers.get(i);
            LayerEntry layerEntry = new LayerEntry(i, l.getName());
            try {
                if (l.getResource() instanceof FeatureTypeInfo info) {
                    GeometryDescriptor geomDesc = info.getFeatureType().getGeometryDescriptor();
                    if (geomDesc != null) {
                        GeometryTypeEnum geomType =
                                GeometryTypeEnum.forJTSClass(geomDesc.getType().getBinding());
                        layerEntry.setGeometryType(geomType);
                    }
                }
            } catch (IOException e) {
                // leave geometry type blank if it couldn't be retrieved, which is seemingly allowed
                // by the FeatureServer API documentation
            }
            this.layers.add(layerEntry);
        }
    }

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public List<LayerEntry> getLayers() {
        return layers;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }
}
