package cn.finalartical.reproduction.admin;

public class ServiceRegistration {
    private String id;
    private String name;
    private String provider;
    private String status;
    private String endpoint;
    private String version;

    public ServiceRegistration() {
    }

    public ServiceRegistration(String id, String name, String provider, String status, String endpoint, String version) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.status = status;
        this.endpoint = endpoint;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
