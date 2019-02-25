package com.yurishchev.aem.tools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DependenciesGenerator {
    private static final String BUNDLE_FILE_NAME = "bundleFile";

    private final List<Dependency> deps = new ArrayList<>();


    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Please specify path to the directory with deployed OSGi bundles.");
        }
        File rootFolder = new File(args[0]);
        if (rootFolder.exists() && rootFolder.isDirectory() && rootFolder.canRead()) {
            Collection<File> bundles = FileUtils.listFiles(rootFolder,
                    TrueFileFilter.INSTANCE, //FileFilterUtils.nameFileFilter(BUNDLE_FILE_NAME),
                    TrueFileFilter.INSTANCE);
            DependenciesGenerator generator = new DependenciesGenerator();
            generator.processBundles(bundles);
        } else {
            throw new IllegalArgumentException("Path does not exist or it is not an accessible folder.");
        }
    }

    private void processBundles(Collection<File> bundles) throws Exception {
        for (File bundle : bundles) {
            processBundle(bundle).ifPresent(deps::add);
        }
        Collections.sort(deps);

        System.out.println("Total number of found bundles: " + bundles.size());
        System.out.println("Total number of found dependencies: " + deps.size());
        System.out.println("**********************************************************************");
        for (Dependency dep : deps) {
            System.out.println(dep);
        }
    }

    /*
     * Extract the pom.xml from each bundle
     */
    private Optional<Dependency> processBundle(File bundle) throws Exception {
        String bundlePath = bundle.getAbsolutePath();
        ZipFile zip = new ZipFile(bundle);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        Optional<Dependency> dependencyFromManifest = Optional.empty();
        Dependency dependencyFromPom = null;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            InputStream pom = zip.getInputStream(entry);

            if (entry.getName().startsWith("META-INF/maven") && entry.getName().endsWith("pom.xml")) {
                dependencyFromPom = getDependencyFromPom(pom, bundlePath);
            }

            if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
                dependencyFromManifest = getDependencyFromManifest(pom, bundlePath);
            }
        }

        if (dependencyFromManifest.isPresent()) {
            if (dependencyFromManifest.get().getGroup() != null) {
                return dependencyFromManifest;
            } else if (dependencyFromPom != null) {
                return Optional.of(dependencyFromPom);
            } else {
                System.out.println("Failed to find pom.xml!");
            }
        }

        return Optional.empty();
    }

    /*
     * Parse the manifest.mf to extract main information
     */
    private Optional<Dependency> getDependencyFromManifest(InputStream stream, String path) throws IOException {
        Manifest manifest = new Manifest(stream);
        Attributes attributes = manifest.getMainAttributes();
        String artifactId = attributes.getValue("Bundle-SymbolicName");
        String version = attributes.getValue("Bundle-Version");
        if (artifactId == null || version == null) {
            System.out.println("Bad manifest data (no artifactId or version metadata provided): " + path);
            return Optional.empty();
        }
        if (artifactId.contains(" ") || !artifactId.contains(".")) {
            System.out.println("Bad artifactId syntax: " + artifactId + ". Path: " + path);
            return Optional.empty();
        }
        if (!artifactId.startsWith("com.liferay")) {
            System.out.println("Non-liferay artifact. Skipping it and trying to get data from pom.xml. ArtifactId: " + artifactId + ". Path: " + path);
            return Optional.of(new Dependency(null, artifactId, version, path));
        }

        return Optional.of(new Dependency("com.liferay", artifactId, version, path));
    }


    /*
     * Parse the pom.xml for groupId, artifactId, version
     */
    private Dependency getDependencyFromPom(InputStream stream, String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(stream);
        Element root = doc.getDocumentElement();
        NodeList nodes = root.getChildNodes();
        Node parent = null;
        Node groupId = null;
        Node artifactId = null;
        Node version = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            switch (node.getNodeName()) {
                case "groupId":
                    groupId = node;
                    break;
                case "artifactId":
                    artifactId = node;
                    break;
                case "version":
                    version = node;
                    break;
                case "parent":
                    parent = node;
                    break;
            }
        }
        if ((groupId == null || version == null) && parent != null) {
            nodes = parent.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeName().equals("groupId") && groupId == null) {
                    groupId = node;
                } else if (node.getNodeName().equals("version") && version == null) {
                    version = node;
                }
            }
        }
        return new Dependency(groupId.getTextContent(), artifactId.getTextContent(), version.getTextContent(), path);
    }

}

class Dependency implements Comparable<Dependency> {
    private final String group;
    private final String artifact;
    private final String version;
    private final String bundlePath;

    Dependency(String _group, String _artifact, String _version, String _bundlePath) {
        group = _group;
        artifact = _artifact;
        version = _version;
        bundlePath = _bundlePath;
    }

    String getGroup() {
        return group;
    }

    private String getArtifact() {
        return artifact;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n<dependency>\n");
        sb.append("    <groupId>").append(group).append("</groupId>\n");
        sb.append("    <artifactId>").append(artifact).append("</artifactId>");
        if (artifact.contains("${")) {
            sb.append("     ************************************************************** ").append(bundlePath);
        }
        sb.append("\n");
        sb.append("    <version>").append(version).append("</version>");
        if (version.contains("${")) {
            sb.append("     ************************************************************** ").append(bundlePath);
        }
        sb.append("\n");
        sb.append("    <scope>provided</scope>\n");
        return sb.append("</dependency>\n").toString();
    }

    public int compareTo(final Dependency dep) {
        int result = group.compareTo(dep.getGroup());
        if (result == 0) {
            return artifact.compareTo(dep.getArtifact());
        } else {
            return result;
        }
    }
}
 