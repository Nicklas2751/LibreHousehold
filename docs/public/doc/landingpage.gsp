<style>
.options {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 2rem;
    margin-top: 2rem;
}

.option-card {
    background: #f8f9fa;
    border-radius: 15px;
    padding: 2rem;
    text-decoration: none;
    color: inherit;
    transition: all 0.3s ease;
    border: 2px solid transparent;
}

.option-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
    border-color: #667eea;
}

.option-icon {
    font-size: 3rem;
    margin-bottom: 1rem;
    display: block;
}

.option-title {
    font-size: 1.5rem;
    font-weight: bold;
    margin-bottom: 1rem;
    color: #333;
}

.option-description {
    color: #666;
    line-height: 1.6;
    margin-bottom: 1.5rem;
}

.option-button {
    background: #667eea;
    color: white;
    padding: 0.8rem 2rem;
    border-radius: 25px;
    text-decoration: none;
    display: inline-block;
    font-weight: 500;
    transition: background 0.3s ease;
}

.option-button:hover {
    background: #5a67d8;
}

@media (max-width: 768px) {

    .options {
        grid-template-columns: 1fr;
    }

}
</style>
<div class="row flex-xl-nowrap">
    <main class="col-12 col-md-12 col-xl-12 pl-md-12" role="main">
        <div class="bg-light p-5 rounded">
            <h1>Software Architecture Documentation</h1>
            <p class="lead">
                Welcome to the LibreHousehold Software Architecture Documentation!
            </p>
            <p>From this point you can find the <a href="overview.html">architecture overview</a> as well as the <a href="chapters/01_introduction_and_goals.html">whole documentation structured by Arc42</a> or jump directly to the <a href="adrs/index.html">Architecture Decision Records</a></p>
        </div>

        <div class="options">
            <a href="overview.html" class="option-card">
                <span class="option-icon">🎯</span>
                <div class="option-title">Architecture Overview</div>
                <div class="option-description">
                    A compact overview of the software architecture. Ideal for a quick start.
                </div>
                <span class="option-button">View Overview</span>
            </a>

            <a href="chapters/01_introduction_and_goals.html" class="option-card">
                <span class="option-icon">📖</span>
                <div class="option-title">Arc42 Documentation</div>
                <div class="option-description">
                    A detailed documentation of the software architecture following the Arc42 template.
                </div>
                <span class="option-button">View whole Documentation</span>
            </a>

            <a href="adrs/index.html" class="option-card">
                <span class="option-icon">📋</span>
                <div class="option-title">Architecture Decision Records</div>
                <div class="option-description">
                    Architecture Decision Records (ADRs) documenting the important decisions.
                </div>
                <span class="option-button">View ADRs</span>
            </a>
        </div>
    </main>

</div>
