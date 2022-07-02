package org.geoserver.gwc;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.config.ConfigurationResourceProvider;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.jdbc.H2Dialect;
import org.geowebcache.diskquota.jdbc.JDBCQuotaStore;
import org.geowebcache.diskquota.jdbc.JDBCQuotaStoreFactory;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.context.ApplicationContext;

/**
 * JDBCH2QuotaStoreFactory
 *
 * <p>Extend the gwc-diskquota-jdbc factory implementation to override the way the H2 database is
 * initialized, supporting v2.x of the h2database library. Since the base getH2DataSource() method
 * is private, we have to resort to overriding the public getQuotaStore() method and duplicating the
 * other methods along the way to getH2DataSource().
 */
public class JDBCH2QuotaStoreFactory extends JDBCQuotaStoreFactory {

    static final Logger LOGGER = Logging.getLogger(JDBCH2QuotaStoreFactory.class);

    public JDBCH2QuotaStoreFactory(final ConfigurationResourceProvider resourceProvider) {
        super(resourceProvider);
    }

    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName)
            throws ConfigurationException {
        // lookup dependencies in the classpath
        DefaultStorageFinder cacheDirFinder =
                (DefaultStorageFinder) ctx.getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator =
                (TilePageCalculator) ctx.getBean("gwcTilePageCalculator");

        if (H2_STORE.equals(quotaStoreName)) {
            return initializeH2Store(cacheDirFinder, tilePageCalculator);
        } else if (JDBC_STORE.equals(quotaStoreName)) {
            // find the base class's private getJDBCStore method
            // and delegate to it - this reflective approach minimizes
            // how much copy/paste and trickery needs to happen in this
            // derived class implementation.
            try {
                Method baseMethod =
                        JDBCQuotaStoreFactory.class.getDeclaredMethod(
                                "getJDBCStore",
                                DefaultStorageFinder.class,
                                TilePageCalculator.class);
                baseMethod.setAccessible(true);
                return (QuotaStore)
                        baseMethod.invoke(
                                JDBCQuotaStoreFactory.class, cacheDirFinder, tilePageCalculator);
            } catch (InvocationTargetException e) {
                // reflective method threw an exception - go ahead and rethrow it
                if (e.getTargetException() instanceof ConfigurationException) {
                    throw (ConfigurationException) e.getTargetException();
                }
                LOGGER.log(
                        Level.FINE,
                        "call to base getJDBCStore method threw unexpected exception",
                        e);
                return null;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                LOGGER.log(
                        Level.WARNING,
                        "reflection error trying to call base getJDBCStore method",
                        e);
                return null;
            }
        }

        return null;
    }

    private QuotaStore initializeH2Store(
            DefaultStorageFinder cacheDirFinder, TilePageCalculator tilePageCalculator)
            throws ConfigurationException {
        // get a default data source located in the cache directory
        DataSource ds = getH2DataSource(cacheDirFinder);

        // build up the store
        JDBCQuotaStore store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
        store.setDataSource(ds);
        store.setDialect(new H2Dialect());

        // initialize it
        store.initialize();

        return store;
    }

    /**
     * Prepares a simple data source for the embedded H2.
     *
     * <p>"overridden" to add the NON_KEYWORDS parameter to the H2 database URL, since the GWC
     * "CREATE TABLE" statement includes a column with an unquoted name of "KEY".
     */
    private DataSource getH2DataSource(DefaultStorageFinder cacheDirFinder)
            throws ConfigurationException {
        File storeDirectory = new File(cacheDirFinder.getDefaultPath(), "diskquota_page_store_h2");
        storeDirectory.mkdirs();

        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        String database = new File(storeDirectory, "diskquota").getAbsolutePath();
        dataSource.setUrl("jdbc:h2:" + database + ";NON_KEYWORDS=KEY");
        dataSource.setUsername("sa");
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(-1); // boundless
        dataSource.setMaxWait(5000);
        return dataSource;
    }
}
