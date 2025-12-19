/**
 * KAGE (影) - Main JavaScript
 * Interactive Logic & Animations
 */

document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

function initApp() {
    // 1. Intro Sequence
    handleIntro();

    // 2. WebGL Background
    initWebGL();

    // 3. Custom Cursor
    initCursor();

    // 4. Scroll Animations (GSAP)
    initScrollAnimations();

    // 5. Terminal Effect
    initTerminal();

    // 6. Vanilla Tilt
    if (typeof VanillaTilt !== 'undefined') {
        VanillaTilt.init(document.querySelectorAll("[data-tilt]"), {
            max: 15,
            speed: 400,
            glare: true,
            "max-glare": 0.2,
        });
    }

    // 7. I18n
    initI18n();

    // 8. Nav Logo Click
    const logo = document.querySelector('.logo');
    if (logo) {
        logo.style.cursor = 'pointer';
        logo.addEventListener('click', () => {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }
}

/* =========================================
   1. Intro Sequence
   ========================================= */
function handleIntro() {
    const intro = document.getElementById('intro');
    let introSkipped = false;

    function finishIntro() {
        if (introSkipped) return;
        introSkipped = true;

        document.body.classList.add('loaded');
        document.body.classList.remove('loading');
        
        // Only run hero animations if gsap is available and hero elements exist
        if (typeof gsap !== 'undefined' && document.querySelector('.hero-title')) {
            gsap.from('.hero-title', {
                duration: 1.5,
                y: 100,
                opacity: 0,
                ease: 'power4.out',
                delay: 0.5
            });
            
            gsap.from('.hero-subtitle', {
                duration: 1,
                y: 20,
                opacity: 0,
                ease: 'power3.out',
                delay: 0.8
            });

            gsap.from('.hero-desc', {
                duration: 1,
                x: -50,
                opacity: 0,
                ease: 'power3.out',
                delay: 1
            });

            gsap.from('.hero-cta', {
                duration: 1,
                scale: 0.8,
                opacity: 0,
                ease: 'back.out(1.7)',
                delay: 1.2
            });
        }
    }

    // If no intro element, just mark as loaded immediately
    if (!intro) {
        document.body.classList.add('loaded');
        document.body.classList.remove('loading');
        return;
    }

    // Auto finish after load + delay
    window.addEventListener('load', () => {
        setTimeout(finishIntro, 2000);
    });

    // Click to skip
    intro.addEventListener('click', finishIntro);
}

/* =========================================
   2. WebGL Background (Three.js)
   ========================================= */
function initWebGL() {
    const canvas = document.querySelector('#webgl-bg');
    if (!canvas) return;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
    const renderer = new THREE.WebGLRenderer({
        canvas: canvas,
        alpha: true,
        antialias: true
    });

    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));

    // Create Particles (Digital Dust / Embers)
    const particlesGeometry = new THREE.BufferGeometry();
    const particlesCount = 700;
    const posArray = new Float32Array(particlesCount * 3);

    for(let i = 0; i < particlesCount * 3; i++) {
        // Spread particles across a wide area
        posArray[i] = (Math.random() - 0.5) * 15;
    }

    particlesGeometry.setAttribute('position', new THREE.BufferAttribute(posArray, 3));

    // Ninja colors: Cyan & Red mix
    const material = new THREE.PointsMaterial({
        size: 0.02,
        color: 0x00ffff,
        transparent: true,
        opacity: 0.8,
        blending: THREE.AdditiveBlending
    });

    const particlesMesh = new THREE.Points(particlesGeometry, material);
    scene.add(particlesMesh);

    // Add a secondary red particle system for contrast
    const particlesGeometry2 = new THREE.BufferGeometry();
    const posArray2 = new Float32Array(particlesCount * 3);
    for(let i = 0; i < particlesCount * 3; i++) {
        posArray2[i] = (Math.random() - 0.5) * 20;
    }
    particlesGeometry2.setAttribute('position', new THREE.BufferAttribute(posArray2, 3));
    const material2 = new THREE.PointsMaterial({
        size: 0.03,
        color: 0xff3366,
        transparent: true,
        opacity: 0.5,
        blending: THREE.AdditiveBlending
    });
    const particlesMesh2 = new THREE.Points(particlesGeometry2, material2);
    scene.add(particlesMesh2);

    // Create "The Core" (Geometric Abstract Representation of Kage)
    const coreGeometry = new THREE.IcosahedronGeometry(1, 0);
    const coreMaterial = new THREE.MeshBasicMaterial({ 
        color: 0x1a0a2e, 
        wireframe: true,
        transparent: true,
        opacity: 0.3
    });
    const coreMesh = new THREE.Mesh(coreGeometry, coreMaterial);
    
    // Inner Core (Glowing)
    const innerGeometry = new THREE.OctahedronGeometry(0.5, 0);
    const innerMaterial = new THREE.MeshBasicMaterial({
        color: 0x00ffff,
        wireframe: true
    });
    const innerMesh = new THREE.Mesh(innerGeometry, innerMaterial);
    
    // Group them
    const coreGroup = new THREE.Group();
    coreGroup.add(coreMesh);
    coreGroup.add(innerMesh);
    
    // Position slightly to the right to balance text
    coreGroup.position.set(2, 0, 0);
    
    // Only add if screen is wide enough
    if (window.innerWidth > 768) {
        scene.add(coreGroup);
    }

    camera.position.z = 3;

    // Mouse Interaction
    let mouseX = 0;
    let mouseY = 0;
    let targetX = 0;
    let targetY = 0;

    const windowHalfX = window.innerWidth / 2;
    const windowHalfY = window.innerHeight / 2;

    document.addEventListener('mousemove', (event) => {
        mouseX = (event.clientX - windowHalfX);
        mouseY = (event.clientY - windowHalfY);
    });

    // Animation Loop
    const clock = new THREE.Clock();

    function animate() {
        const elapsedTime = clock.getElapsedTime();

        targetX = mouseX * 0.001;
        targetY = mouseY * 0.001;

        // Smooth rotation based on mouse
        particlesMesh.rotation.y += 0.05 * (targetX - particlesMesh.rotation.y);
        particlesMesh.rotation.x += 0.05 * (targetY - particlesMesh.rotation.x);

        particlesMesh2.rotation.y += 0.02 * (targetX - particlesMesh2.rotation.y);
        particlesMesh2.rotation.x += 0.02 * (targetY - particlesMesh2.rotation.x);

        // Gentle floating animation
        particlesMesh.position.y = Math.sin(elapsedTime * 0.5) * 0.2;
        particlesMesh2.position.y = Math.cos(elapsedTime * 0.3) * 0.2;

        // Animate Core
        if (coreGroup) {
            coreGroup.rotation.x += 0.002;
            coreGroup.rotation.y += 0.005;
            // Float effect
            coreGroup.position.y = Math.sin(elapsedTime * 0.8) * 0.1;
        }

        renderer.render(scene, camera);
        requestAnimationFrame(animate);
    }

    animate();

    // Handle Resize
    window.addEventListener('resize', () => {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    });
}

/* =========================================
   3. Custom Cursor
   ========================================= */
function initCursor() {
    const cursor = document.getElementById('cursor');
    if (!cursor) return;
    const circle = cursor.querySelector('.cursor-circle');
    const dot = cursor.querySelector('.cursor-dot');

    let mouseX = 0;
    let mouseY = 0;
    let circleX = 0;
    let circleY = 0;
    let dotX = 0;
    let dotY = 0;

    document.addEventListener('mousemove', (e) => {
        mouseX = e.clientX;
        mouseY = e.clientY;
    });

    function animateCursor() {
        // Smooth follow for circle
        circleX += (mouseX - circleX) * 0.15;
        circleY += (mouseY - circleY) * 0.15;
        
        // Faster follow for dot
        dotX += (mouseX - dotX) * 0.4;
        dotY += (mouseY - dotY) * 0.4;

        cursor.style.left = `${circleX}px`;
        cursor.style.top = `${circleY}px`;
        
        // Micro-adjustments for inner parts if needed
        circle.style.transform = `translate(-50%, -50%) scale(${1 + Math.abs(mouseX - circleX) * 0.001})`;
        
        requestAnimationFrame(animateCursor);
    }

    animateCursor();

    // Hover effects
    const interactiveElements = document.querySelectorAll('a, button, .feature-card, .logo');
    interactiveElements.forEach(el => {
        el.addEventListener('mouseenter', () => {
            circle.style.transform = 'translate(-50%, -50%) scale(1.5)';
            circle.style.borderColor = 'var(--ninja-red)';
            circle.style.backgroundColor = 'rgba(255, 51, 102, 0.1)';
        });
        el.addEventListener('mouseleave', () => {
            circle.style.transform = 'translate(-50%, -50%) scale(1)';
            circle.style.borderColor = 'var(--ninja-cyan)';
            circle.style.backgroundColor = 'transparent';
        });
    });
}

/* =========================================
   4. Scroll Animations
   ========================================= */
function initScrollAnimations() {
    if (typeof gsap === 'undefined' || typeof ScrollTrigger === 'undefined') return;

    gsap.registerPlugin(ScrollTrigger);

    // Features Section - only if elements exist
    const featureCards = document.querySelectorAll('.feature-card');
    const featuresSection = document.querySelector('.features');
    if (featureCards.length > 0 && featuresSection) {
        gsap.from('.feature-card', {
            scrollTrigger: {
                trigger: '.features',
                start: 'top 80%',
            },
            y: 100,
            opacity: 0,
            duration: 0.8,
            stagger: 0.2,
            ease: 'power3.out'
        });
    }

    // Section Titles
    document.querySelectorAll('.section-title').forEach(title => {
        gsap.from(title, {
            scrollTrigger: {
                trigger: title,
                start: 'top 85%',
            },
            y: 50,
            opacity: 0,
            duration: 1,
            ease: 'power3.out'
        });
    });
}

/* =========================================
   5. Terminal Effect
   ========================================= */
function initTerminal() {
    const typewriterElement = document.getElementById('typewriter');
    const commandList = document.getElementById('command-list');
    
    if (!typewriterElement || !commandList || typeof Typewriter === 'undefined') return;

    // Commands to display - actual Discord bot commands
    const commands = [
        { cmd: 'ask', desc: 'AI 对话，支持联网搜索' },
        { cmd: 'play', desc: '播放音乐' },
        { cmd: 'lottery', desc: '发起抽奖活动' },
        { cmd: 'poll', desc: '创建投票' },
        { cmd: 'fortune', desc: '今日运势 & 塔罗牌' },
        { cmd: 'countdown', desc: '下班倒计时' },
        { cmd: 'remind', desc: '设置定时提醒' },
        { cmd: 'clear', desc: '清除对话历史' }
    ];

    const typewriter = new Typewriter(typewriterElement, {
        loop: true,
        delay: 75,
        cursor: '█'
    });

    typewriter
        .pauseFor(2500)
        .typeString('/help')
        .pauseFor(500)
        .callFunction(() => {
            // Populate grid
            commandList.innerHTML = '';
            commands.forEach((item, index) => {
                setTimeout(() => {
                    const div = document.createElement('div');
                    div.className = 'cmd-item';
                    div.innerHTML = `<span class="cmd-name">/${item.cmd}</span><span class="cmd-desc">${item.desc}</span>`;
                    commandList.appendChild(div);
                }, index * 200);
            });
        })
        .pauseFor(5000)
        .deleteAll()
        .typeString('/ask 今天天气怎么样？')
        .pauseFor(1000)
        .deleteAll()
        .start();
}

/* =========================================
   7. I18n (Language Switching)
   ========================================= */
function initI18n() {
    const langBtn = document.getElementById('lang-switch');
    let currentLang = 'en'; // Default

    const translations = {
        en: {
            "nav.base": "BASE",
            "nav.features": "SCROLLS",
            "nav.commands": "JUTSU",
            "hero.subtitle": "SHADOW STEWARD",
            "hero.title": "KAGEI",
            "hero.desc": "The Silent Guardian for Your Discord Realm.",
            "hero.cta": "SUMMON KAGE",
            "hero.scroll": "SCROLL",
            "features.title": "HIDDEN SCROLLS",
            "features.guardian.title": "Intelligence",
            "features.guardian.desc": "Powered by DeepSeek & Jina MCP for real-time web search.",
            "features.melody.title": "Melody",
            "features.melody.desc": "High-fidelity music playback with seamless streaming.",
            "features.economy.title": "Fortune",
            "features.economy.desc": "Tarot reading, Daily Fortune, and Lottery system.",
            "features.entertainment.title": "Tools",
            "features.entertainment.desc": "Polls, Off-work Countdowns, and Reminders.",
            "commands.title": "SECRET JUTSU",
            "footer.terms": "Terms",
            "footer.privacy": "Privacy"
        },
        zh: {
            "nav.base": "基地",
            "nav.features": "卷轴",
            "nav.commands": "忍术",
            "hero.subtitle": "影之家令",
            "hero.title": "影 · 家令",
            "hero.desc": "隐于暗处，默默守护你的 Discord 服务器。",
            "hero.cta": "召唤影",
            "hero.scroll": "卷轴",
            "features.title": "秘传卷轴",
            "features.guardian.title": "智慧",
            "features.guardian.desc": "由 DeepSeek & Jina MCP 驱动，支持实时联网搜索。",
            "features.melody.title": "旋律",
            "features.melody.desc": "高保真音乐播放，无缝流媒体体验。",
            "features.economy.title": "运势",
            "features.economy.desc": "塔罗牌占卜，每日运势，以及抽奖系统。",
            "features.entertainment.title": "工具",
            "features.entertainment.desc": "投票系统，下班倒计时，以及提醒事项。",
            "commands.title": "秘传忍术",
            "footer.terms": "服务条款",
            "footer.privacy": "隐私政策"
        }
    };

    function updateText() {
        const elements = document.querySelectorAll('[data-i18n]');
        elements.forEach(el => {
            const key = el.getAttribute('data-i18n');
            const targetAttr = el.getAttribute('data-i18n-target'); // For data-text attributes (glitch effect)
            
            if (translations[currentLang][key]) {
                if (targetAttr) {
                    el.setAttribute(targetAttr, translations[currentLang][key]);
                    el.innerText = translations[currentLang][key]; // Update inner text too usually
                } else {
                    el.innerText = translations[currentLang][key];
                }
            }

            // Update href for links with language-specific URLs
            const hrefAttr = currentLang === 'en' ? 'data-href-en' : 'data-href-zh';
            if (el.hasAttribute(hrefAttr)) {
                el.href = el.getAttribute(hrefAttr);
            }
        });

        langBtn.innerText = currentLang === 'en' ? 'CN / EN' : 'EN / CN';
    }

    if (langBtn) {
        langBtn.addEventListener('click', () => {
            currentLang = currentLang === 'en' ? 'zh' : 'en';
            updateText();
        });
    }
}
