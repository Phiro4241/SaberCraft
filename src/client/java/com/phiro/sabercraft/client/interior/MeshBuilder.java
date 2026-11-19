package com.phiro.sabercraft.client.interior;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;

/**
 * Phase 15: builds the models of the interiors and hulls from shapes: textured rectangles and
 * polygons (split along the texture grid so the atlas tiles repeat), boxes, prisms, cylinders,
 * cones, spheres and pipes. A "pen" holds what the next shapes look like (atlas tile, how many
 * blocks one tile covers, colour, layer, light, animation); a transform stack places parts.
 *
 * <p>Conventions: plot space in blocks (x east, y up, z south). A rectangle is given by its top
 * left corner as seen from its front, the edge to the right and the edge downwards; polygons are
 * counter-clockwise as seen from the front. Only the front is drawn unless the pen is two-sided.
 */
public final class MeshBuilder {
	/** The layers of a scene, each drawn with its own render type. */
	public enum Layer {
		/** Lit, opaque (with cut-out holes), back faces culled. */
		SOLID,
		/** Unlit additive light (screens, lamps, engine glow), drawn over the solid layer. */
		GLOW,
		/** Lit, see-through (window panes). */
		GLASS,
		/** Unlit, see-through (holograms, force fields). */
		HOLO,
		/**
		 * Phase 25: light thrown onto surfaces by something glowing (the reactor's core on the floor
		 * round it, a hologram on its table): added to what is there, so it brightens and tints and
		 * never darkens; black adds nothing. See {@link MeshBuilder#lightFrom}.
		 */
		LIGHT
	}

	/** Where finished quads go. */
	public interface Sink {
		void quad(Layer layer, float[] xyzuv, float nx, float ny, float nz, int[] colours, int lightMode, int fixedLight, int anim, int[] samples);
	}

	private static final float INSET = 0.5F / InteriorAtlas.TILE;

	private final Sink sink;
	private final Deque<Matrix4f> stack = new ArrayDeque<>();
	private Matrix4f matrix = new Matrix4f();
	private final Matrix3f normalMatrix = new Matrix3f();

	// The pen.
	private int tile = InteriorAtlas.WHITE;
	private @Nullable Vector3f lightA;
	private @Nullable Vector3f lightB;
	private float lightInner;
	private float lightRadius;
	private float scale = 1.0F;
	private int colour = 0xFFFFFFFF;
	private Layer layer = Layer.SOLID;
	private int lightMode = Mesh.LIGHT_WORLD;
	private int fixedLight;
	private int anim;
	private boolean twoSided;
	private boolean fit;
	private float subU0;
	private float subV0;
	private float subU1 = 1.0F;
	private float subV1 = 1.0F;
	private Vector3f sun;
	private float sunAmbient = 0.6F;
	/** Other tiles that may stand in for the pen's tile, picked per tile cell (for big plated surfaces). */
	private int[] variants;
	private int variantSeed;

	private int quads;

	public MeshBuilder(Sink sink) {
		this.sink = sink;
	}

	// --- The pen -----------------------------------------------------------------------------

	/** The atlas tile, repeating every {@code blocks} blocks. Ends {@link #fit}. */
	public MeshBuilder tile(int tile, float blocks) {
		this.variants = null;
		this.tile = tile;
		this.scale = blocks;
		this.fit = false;
		this.subU0 = 0.0F;
		this.subV0 = 0.0F;
		this.subU1 = 1.0F;
		this.subV1 = 1.0F;
		return this;
	}

	public MeshBuilder tile(int tile) {
		return this.tile(tile, 1.0F);
	}

	/** Like {@link #tile(int, float)}, but each tile cell takes one of the tiles, chosen at random by its place. */
	public MeshBuilder tiles(float blocks, int seed, int... tiles) {
		this.tile(tiles[0], blocks);
		this.variants = tiles.clone();
		this.variantSeed = seed;
		return this;
	}

	/** Multiplies the transform by a basis: x to {@code right}, y to {@code up}, z to {@code back}. */
	public MeshBuilder basis(Vector3f right, Vector3f up, Vector3f back) {
		this.matrix.mul(new Matrix4f(right.x, right.y, right.z, 0.0F, up.x, up.y, up.z, 0.0F, back.x, back.y, back.z, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F));
		return this;
	}

	/** The next rectangles show the whole tile (or the part u0..u1, v0..v1 of it) once, stretched. */
	/**
	 * Phase 25: the next quads are lit by a light along the line from {@code a} to {@code b} (or at
	 * a point, a = b): each corner gets the colour by how near it is, full at the light, fading
	 * softly to nothing at {@code radius} (for the {@link Layer#LIGHT} layer). In the builder's own
	 * space, like the points of the quads.
	 */
	public MeshBuilder lightFrom(Vector3f a, Vector3f b, float radius) {
		return this.lightFrom(a, b, 0.0F, radius);
	}

	/** As above, full up to {@code inner} from the light (the size of what glows), fading to {@code radius}. */
	public MeshBuilder lightFrom(Vector3f a, Vector3f b, float inner, float radius) {
		this.lightA = new Vector3f(a);
		this.lightB = new Vector3f(b);
		this.lightInner = inner;
		this.lightRadius = radius;
		return this;
	}

	/** Phase 25: the next quads no longer fade from a light. */
	public MeshBuilder noLight() {
		this.lightA = null;
		this.lightB = null;
		return this;
	}

	/** Phase 25: how strongly a light lights a point: 1 up to {@code inner}, 0 from {@code radius} on, soft in between. */
	static float falloff(Vector3f point, Vector3f a, Vector3f b, float inner, float radius) {
		Vector3f ab = new Vector3f(b).sub(a);
		float len = ab.lengthSquared();
		float t = len < 1.0E-6F ? 0.0F : Mth.clamp(new Vector3f(point).sub(a).dot(ab) / len, 0.0F, 1.0F);
		float d = new Vector3f(a).add(ab.mul(t)).distance(point);
		float k = Mth.clamp(1.0F - Math.max(0.0F, d - inner) / Math.max(1.0E-3F, radius - inner), 0.0F, 1.0F);
		return k * k;
	}

	public MeshBuilder fit(int tile) {
		return this.fit(tile, 0.0F, 0.0F, 1.0F, 1.0F);
	}

	public MeshBuilder fit(int tile, float u0, float v0, float u1, float v1) {
		this.tile = tile;
		this.fit = true;
		this.subU0 = u0;
		this.subV0 = v0;
		this.subU1 = u1;
		this.subV1 = v1;
		return this;
	}

	public MeshBuilder colour(int argb) {
		this.colour = argb | (argb >>> 24 == 0 ? 0xFF000000 : 0);
		return this;
	}

	/** A colour with an explicit alpha (for see-through layers). */
	public MeshBuilder colourAlpha(int argb) {
		this.colour = argb;
		return this;
	}

	public MeshBuilder white() {
		this.colour = 0xFFFFFFFF;
		return this;
	}

	public MeshBuilder layer(Layer layer) {
		this.layer = layer;
		return this;
	}

	public MeshBuilder solid() {
		return this.layer(Layer.SOLID);
	}

	/** Lit by the blocks around (the default). */
	public MeshBuilder worldLight() {
		this.lightMode = Mesh.LIGHT_WORLD;
		return this;
	}

	public MeshBuilder fullBright() {
		this.lightMode = Mesh.LIGHT_FULL;
		return this;
	}

	public MeshBuilder fixedLight(int block, int sky) {
		this.lightMode = Mesh.LIGHT_FIXED;
		this.fixedLight = net.minecraft.util.LightCoordsUtil.pack(block, sky);
		return this;
	}

	/** Animation id for the next shapes (0 = still); see {@link InteriorScene}. */
	public MeshBuilder anim(int anim) {
		this.anim = anim;
		return this;
	}

	private boolean inside;

	/** Phase 17: whether the next shapes belong to the rooms (they dim with the ship's lights). */
	public MeshBuilder inside(boolean inside) {
		this.inside = inside;
		return this;
	}

	public MeshBuilder twoSided(boolean twoSided) {
		this.twoSided = twoSided;
		return this;
	}

	/** Bakes a light direction into the colours (for hulls in open space), or none. */
	public MeshBuilder sun(Vector3f direction, float ambient) {
		this.sun = direction == null ? null : new Vector3f(direction).normalize();
		this.sunAmbient = ambient;
		return this;
	}

	public int quads() {
		return this.quads;
	}

	// --- Transforms ----------------------------------------------------------------------------

	public MeshBuilder push() {
		this.stack.push(new Matrix4f(this.matrix));
		return this;
	}

	public MeshBuilder pop() {
		this.matrix = this.stack.pop();
		return this;
	}

	public MeshBuilder translate(double x, double y, double z) {
		this.matrix.translate((float) x, (float) y, (float) z);
		return this;
	}

	/** Turns around y by degrees (90 turns +z onto +x... as Minecraft's yaw would: south to west is +90). */
	public MeshBuilder rotateY(float degrees) {
		this.matrix.rotateY(degrees * Mth.DEG_TO_RAD);
		return this;
	}

	public MeshBuilder rotateX(float degrees) {
		this.matrix.rotateX(degrees * Mth.DEG_TO_RAD);
		return this;
	}

	public MeshBuilder rotateZ(float degrees) {
		this.matrix.rotateZ(degrees * Mth.DEG_TO_RAD);
		return this;
	}

	public MeshBuilder scale(float x, float y, float z) {
		this.matrix.scale(x, y, z);
		return this;
	}

	// --- Rectangles and polygons -----------------------------------------------------------------

	/** A rectangle: top left corner {@code o} seen from the front, {@code du} to the right, {@code dv} down. */
	public MeshBuilder rect(Vector3f o, Vector3f du, Vector3f dv) {
		if (this.fit) {
			Vector3f a = new Vector3f(o);
			Vector3f b = new Vector3f(o).add(dv);
			Vector3f c = new Vector3f(o).add(du).add(dv);
			Vector3f d = new Vector3f(o).add(du);
			Vector3f n = new Vector3f(dv).cross(du).normalize();
			this.emit(new Vector3f[] {a, b, c, d}, new float[] {this.subU0, this.subU0, this.subU1, this.subU1},
					new float[] {this.subV0, this.subV1, this.subV1, this.subV0}, n);
			return this;
		}

		return this.poly(List.of(new Vector3f(o), new Vector3f(o).add(dv), new Vector3f(o).add(du).add(dv), new Vector3f(o).add(du)));
	}

	public MeshBuilder rect(float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz) {
		return this.rect(new Vector3f(ox, oy, oz), new Vector3f(ux, uy, uz), new Vector3f(vx, vy, vz));
	}

	/** A flat convex polygon, counter-clockwise as seen from the front. */
	public MeshBuilder poly(List<Vector3f> points) {
		if (points.size() < 3) {
			return this;
		}

		Vector3f p0 = points.get(0);
		Vector3f n = new Vector3f();

		// Newell's method: robust for any planar polygon.
		for (int i = 0; i < points.size(); i++) {
			Vector3f a = points.get(i);
			Vector3f b = points.get((i + 1) % points.size());
			n.x += (a.y - b.y) * (a.z + b.z);
			n.y += (a.z - b.z) * (a.x + b.x);
			n.z += (a.x - b.x) * (a.y + b.y);
		}

		if (n.lengthSquared() < 1.0E-10F) {
			return this;
		}

		n.normalize();
		Vector3f du;
		Vector3f dv;

		if (Math.abs(n.y) > 0.7F) {
			// Floors and ceilings: x to the right, projected into the plane (for tilted ones).
			du = new Vector3f(1.0F, 0.0F, 0.0F);
			du.sub(new Vector3f(n).mul(n.dot(du))).normalize();
			dv = new Vector3f(du).cross(n).normalize();
		} else {
			dv = new Vector3f(0.0F, -1.0F, 0.0F);
			dv.sub(new Vector3f(n).mul(n.dot(dv))).normalize();
			du = new Vector3f(n).cross(dv).normalize();
		}

		// dv x du must be n: fix the handedness if needed.
		if (new Vector3f(dv).cross(du).dot(n) < 0.0F) {
			du.negate();
		}

		float s = this.fit ? 1.0F : this.scale;
		int k = points.size();
		float[] a = new float[k];
		float[] b = new float[k];
		float minA = Float.MAX_VALUE;
		float maxA = -Float.MAX_VALUE;
		float minB = Float.MAX_VALUE;
		float maxB = -Float.MAX_VALUE;

		for (int i = 0; i < k; i++) {
			a[i] = points.get(i).dot(du) / s;
			b[i] = points.get(i).dot(dv) / s;
			minA = Math.min(minA, a[i]);
			maxA = Math.max(maxA, a[i]);
			minB = Math.min(minB, b[i]);
			maxB = Math.max(maxB, b[i]);
		}

		float a0 = p0.dot(du);
		float b0 = p0.dot(dv);

		if (this.fit) {
			// One tile across the polygon's bounds.
			Vector3f[] corners = points.toArray(new Vector3f[0]);
			float[] us = new float[k];
			float[] vs = new float[k];

			for (int i = 0; i < k; i++) {
				us[i] = Mth.lerp((a[i] - minA) / Math.max(1.0E-4F, maxA - minA), this.subU0, this.subU1);
				vs[i] = Mth.lerp((b[i] - minB) / Math.max(1.0E-4F, maxB - minB), this.subV0, this.subV1);
			}

			this.fan(corners, us, vs, n);
			return this;
		}

		for (int ca = Mth.floor(minA + 1.0E-4F); ca < maxA - 1.0E-4F; ca++) {
			for (int cb = Mth.floor(minB + 1.0E-4F); cb < maxB - 1.0E-4F; cb++) {
				List<float[]> clipped = clip(a, b, ca, cb, ca + 1, cb + 1);

				if (clipped.size() < 3) {
					continue;
				}

				int saved = this.tile;

				if (this.variants != null) {
					int h = ca * 73856093 ^ cb * 19349663 ^ this.variantSeed * 83492791;
					h ^= h >>> 13;
					h *= 0x5bd1e995;
					h ^= h >>> 15;
					this.tile = this.variants[Math.floorMod(h, this.variants.length)];
				}

				Vector3f[] corners = new Vector3f[clipped.size()];
				float[] us = new float[clipped.size()];
				float[] vs = new float[clipped.size()];

				for (int i = 0; i < clipped.size(); i++) {
					float pa = clipped.get(i)[0];
					float pb = clipped.get(i)[1];
					corners[i] = new Vector3f(p0).add(new Vector3f(du).mul(pa * s - a0)).add(new Vector3f(dv).mul(pb * s - b0));
					us[i] = pa - ca;
					vs[i] = pb - cb;
				}

				this.fan(corners, us, vs, n);
				this.tile = saved;
			}
		}

		return this;
	}

	public MeshBuilder poly(float... xyz) {
		List<Vector3f> points = new ArrayList<>();

		for (int i = 0; i + 2 < xyz.length; i += 3) {
			points.add(new Vector3f(xyz[i], xyz[i + 1], xyz[i + 2]));
		}

		return this.poly(points);
	}

	/** Clips a polygon (in tile units) to one tile cell (Sutherland-Hodgman). */
	private static List<float[]> clip(float[] a, float[] b, float x0, float y0, float x1, float y1) {
		List<float[]> poly = new ArrayList<>(a.length + 4);

		for (int i = 0; i < a.length; i++) {
			poly.add(new float[] {a[i], b[i]});
		}

		poly = clipEdge(poly, 0, x0, true);
		poly = clipEdge(poly, 0, x1, false);
		poly = clipEdge(poly, 1, y0, true);
		poly = clipEdge(poly, 1, y1, false);
		return poly;
	}

	private static List<float[]> clipEdge(List<float[]> poly, int axis, float bound, boolean min) {
		List<float[]> out = new ArrayList<>(poly.size() + 2);

		for (int i = 0; i < poly.size(); i++) {
			float[] p = poly.get(i);
			float[] q = poly.get((i + 1) % poly.size());
			boolean pin = min ? p[axis] >= bound : p[axis] <= bound;
			boolean qin = min ? q[axis] >= bound : q[axis] <= bound;

			if (pin) {
				out.add(p);
			}

			if (pin != qin) {
				float t = (bound - p[axis]) / (q[axis] - p[axis]);
				out.add(new float[] {p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
			}
		}

		return out;
	}

	/** A convex polygon as quads (a fan, the last one folded into a triangle if needed). */
	private void fan(Vector3f[] p, float[] u, float[] v, Vector3f n) {
		for (int i = 1; i + 1 < p.length; i += 2) {
			int j = Math.min(i + 2, p.length - 1);
			this.emit(new Vector3f[] {p[0], p[i], p[i + 1], p[j]}, new float[] {u[0], u[i], u[i + 1], u[j]},
					new float[] {v[0], v[i], v[i + 1], v[j]}, n);
		}
	}

	// --- Solids --------------------------------------------------------------------------------

	public static final int EAST = 1;
	public static final int WEST = 2;
	public static final int UP = 4;
	public static final int DOWN = 8;
	public static final int SOUTH = 16;
	public static final int NORTH = 32;
	public static final int ALL = 63;

	/** A box between two corners. */
	public MeshBuilder box(double x0, double y0, double z0, double x1, double y1, double z1) {
		return this.box(x0, y0, z0, x1, y1, z1, ALL);
	}

	/** A box, only the faces in {@code faces} ({@link #EAST}, ...). */
	public MeshBuilder box(double x0d, double y0d, double z0d, double x1d, double y1d, double z1d, int faces) {
		float x0 = (float) Math.min(x0d, x1d);
		float x1 = (float) Math.max(x0d, x1d);
		float y0 = (float) Math.min(y0d, y1d);
		float y1 = (float) Math.max(y0d, y1d);
		float z0 = (float) Math.min(z0d, z1d);
		float z1 = (float) Math.max(z0d, z1d);
		float w = x1 - x0;
		float h = y1 - y0;
		float d = z1 - z0;

		if ((faces & UP) != 0) {
			this.rect(x0, y1, z0, w, 0, 0, 0, 0, d);
		}

		if ((faces & DOWN) != 0) {
			this.rect(x0, y0, z1, w, 0, 0, 0, 0, -d);
		}

		if ((faces & NORTH) != 0) {
			this.rect(x1, y1, z0, -w, 0, 0, 0, -h, 0);
		}

		if ((faces & SOUTH) != 0) {
			this.rect(x0, y1, z1, w, 0, 0, 0, -h, 0);
		}

		if ((faces & WEST) != 0) {
			this.rect(x0, y1, z0, 0, 0, d, 0, -h, 0);
		}

		if ((faces & EAST) != 0) {
			this.rect(x1, y1, z1, 0, 0, -d, 0, -h, 0);
		}

		return this;
	}

	/**
	 * An upright prism: the polygon {@code xz} (pairs, counter-clockwise seen from above) from
	 * {@code y0} to {@code y1}, with its top and bottom if asked.
	 */
	public MeshBuilder prism(float[] xz, float y0, float y1, boolean top, boolean bottom) {
		int k = xz.length / 2;

		for (int i = 0; i < k; i++) {
			float ax = xz[i * 2];
			float az = xz[i * 2 + 1];
			float bx = xz[(i + 1) % k * 2];
			float bz = xz[(i + 1) % k * 2 + 1];
			Vector3f out = new Vector3f(bz - az, 0.0F, -(bx - ax)).mul(area(xz) > 0.0F ? 1.0F : -1.0F);
			this.polyFacing(List.of(new Vector3f(bx, y1, bz), new Vector3f(bx, y0, bz), new Vector3f(ax, y0, az), new Vector3f(ax, y1, az)), out);
		}

		List<Vector3f> up = new ArrayList<>();
		List<Vector3f> down = new ArrayList<>();

		for (int i = 0; i < k; i++) {
			up.add(new Vector3f(xz[i * 2], y1, xz[i * 2 + 1]));
			down.add(0, new Vector3f(xz[i * 2], y0, xz[i * 2 + 1]));
		}

		if (top) {
			this.polyFacing(up, new Vector3f(0.0F, 1.0F, 0.0F));
		}

		if (bottom) {
			this.polyFacing(down, new Vector3f(0.0F, -1.0F, 0.0F));
		}

		return this;
	}

	/** Twice the signed area of a polygon in the xz plane (positive when it turns from x to z). */
	private static float area(float[] xz) {
		float sum = 0.0F;
		int k = xz.length / 2;

		for (int i = 0; i < k; i++) {
			int j = (i + 1) % k;
			sum += xz[i * 2] * xz[j * 2 + 1] - xz[j * 2] * xz[i * 2 + 1];
		}

		return sum;
	}

	/** A flat polygon facing {@code direction} (its points in either order). */
	public MeshBuilder polyFacing(List<Vector3f> points, Vector3f direction) {
		Vector3f n = new Vector3f();

		for (int i = 0; i < points.size(); i++) {
			Vector3f a = points.get(i);
			Vector3f b = points.get((i + 1) % points.size());
			n.x += (a.y - b.y) * (a.z + b.z);
			n.y += (a.z - b.z) * (a.x + b.x);
			n.z += (a.x - b.x) * (a.y + b.y);
		}

		return this.poly(n.dot(direction) >= 0.0F ? points : reverse(points));
	}

	private static List<Vector3f> reverse(List<Vector3f> list) {
		List<Vector3f> out = new ArrayList<>(list);
		java.util.Collections.reverse(out);
		return out;
	}

	/**
	 * A cylinder or cone frustum along an axis: from {@code base} along {@code axis} (its length is
	 * the height), radius {@code r0} at the base and {@code r1} at the top. The texture wraps once
	 * around ({@code uRepeat} times) and runs along the axis. Caps if asked.
	 */
	public MeshBuilder cylinder(Vector3f base, Vector3f axis, float r0, float r1, int segments, float uRepeat, boolean capBase, boolean capTop) {
		float length = axis.length();
		Vector3f dir = new Vector3f(axis).normalize();
		Vector3f side = Math.abs(dir.y) < 0.9F ? new Vector3f(0, 1, 0).cross(dir).normalize() : new Vector3f(1, 0, 0).cross(dir).normalize();
		Vector3f up = new Vector3f(dir).cross(side).normalize();
		Vector3f top = new Vector3f(base).add(axis);
		float slope = (r0 - r1) / Math.max(1.0E-4F, length);

		for (int i = 0; i < segments; i++) {
			float a0 = Mth.TWO_PI * i / segments;
			float a1 = Mth.TWO_PI * (i + 1) / segments;
			Vector3f d0 = new Vector3f(side).mul(Mth.cos(a0)).add(new Vector3f(up).mul(Mth.sin(a0)));
			Vector3f d1 = new Vector3f(side).mul(Mth.cos(a1)).add(new Vector3f(up).mul(Mth.sin(a1)));
			Vector3f b0 = new Vector3f(base).add(new Vector3f(d0).mul(r0));
			Vector3f b1 = new Vector3f(base).add(new Vector3f(d1).mul(r0));
			Vector3f t0 = new Vector3f(top).add(new Vector3f(d0).mul(r1));
			Vector3f t1 = new Vector3f(top).add(new Vector3f(d1).mul(r1));
			Vector3f n = new Vector3f(d0).add(d1).normalize().add(new Vector3f(dir).mul(slope)).normalize();
			float u0 = uRepeat * i / segments;
			float u1 = uRepeat * (i + 1) / segments;
			// UV v across (0..1 around the pipe), u along.
			this.emitWrapped(new Vector3f[] {t1, b1, b0, t0}, new float[] {u1, u1, u0, u0}, new float[] {0.0F, 1.0F, 1.0F, 0.0F}, n);

			if (capBase && r0 > 0.0F) {
				this.emitTri(base, b0, b1, dir, true);
			}

			if (capTop && r1 > 0.0F) {
				this.emitTri(top, t1, t0, dir, false);
			}
		}

		return this;
	}

	/** Texture coordinates beyond one tile wrap: split into tile-sized pieces along u. */
	private void emitWrapped(Vector3f[] p, float[] u, float[] v, Vector3f n) {
		float min = Math.min(u[2], u[0]);
		float max = Math.max(u[2], u[0]);
		int start = Mth.floor(min);

		if (max - start <= 1.0001F) {
			float[] uu = new float[4];

			for (int i = 0; i < 4; i++) {
				uu[i] = u[i] - start;
			}

			this.emitTile(p, uu, v, n);
			return;
		}

		// Rare: a segment crossing a tile edge; draw it in two halves.
		float cut = start + 1.0F;
		float t = (cut - min) / (max - min);
		Vector3f mTop = new Vector3f(p[3]).lerp(p[0], t);
		Vector3f mBottom = new Vector3f(p[2]).lerp(p[1], t);
		this.emitTile(new Vector3f[] {mTop, mBottom, p[2], p[3]}, new float[] {1.0F, 1.0F, min - start, min - start}, v, n);
		this.emitTile(new Vector3f[] {p[0], p[1], mBottom, mTop}, new float[] {max - cut, max - cut, 0.0F, 0.0F}, v, n);
	}

	private void emitTri(Vector3f a, Vector3f b, Vector3f c, Vector3f axis, boolean base) {
		Vector3f n = base ? new Vector3f(axis).negate() : new Vector3f(axis);
		this.emitTile(new Vector3f[] {a, b, c, c}, new float[] {0.5F, 0.0F, 1.0F, 1.0F}, new float[] {0.5F, 1.0F, 1.0F, 1.0F}, n);
	}

	/** A pipe through points, with round joints where it bends. */
	public MeshBuilder pipe(float radius, int segments, float... xyz) {
		for (int i = 0; i + 5 < xyz.length; i += 3) {
			Vector3f a = new Vector3f(xyz[i], xyz[i + 1], xyz[i + 2]);
			Vector3f b = new Vector3f(xyz[i + 3], xyz[i + 4], xyz[i + 5]);
			Vector3f axis = new Vector3f(b).sub(a);
			this.cylinder(a, axis, radius, radius, segments, Math.max(1.0F, Math.round(axis.length() / 2.0F)), false, false);

			if (i > 0) {
				this.sphere(a, radius * 1.02F, segments, segments / 2);
			}
		}

		return this;
	}

	/** A sagging cable from a to b, hanging {@code sag} blocks in the middle. */
	public MeshBuilder cable(Vector3f a, Vector3f b, float sag, float radius) {
		int steps = 8;
		float[] points = new float[(steps + 1) * 3];

		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			Vector3f p = new Vector3f(a).lerp(b, t);
			p.y -= sag * 4.0F * t * (1.0F - t);
			points[i * 3] = p.x;
			points[i * 3 + 1] = p.y;
			points[i * 3 + 2] = p.z;
		}

		return this.pipe(radius, 5, points);
	}

	/** A sphere (or a dome with {@code fromLat} > -90). */
	public MeshBuilder sphere(Vector3f centre, float radius, int segments, int rings) {
		return this.dome(centre, radius, segments, rings, -90.0F, 90.0F);
	}

	public MeshBuilder dome(Vector3f centre, float radius, int segments, int rings, float fromLat, float toLat) {
		for (int r = 0; r < rings; r++) {
			float la0 = Mth.lerp(r / (float) rings, fromLat, toLat) * Mth.DEG_TO_RAD;
			float la1 = Mth.lerp((r + 1) / (float) rings, fromLat, toLat) * Mth.DEG_TO_RAD;

			for (int s = 0; s < segments; s++) {
				float lo0 = Mth.TWO_PI * s / segments;
				float lo1 = Mth.TWO_PI * (s + 1) / segments;
				Vector3f a = point(centre, radius, la1, lo0);
				Vector3f b = point(centre, radius, la0, lo0);
				Vector3f c = point(centre, radius, la0, lo1);
				Vector3f d = point(centre, radius, la1, lo1);
				Vector3f n = new Vector3f(a).add(b).add(c).add(d).mul(0.25F).sub(centre).normalize();
				this.emitTile(new Vector3f[] {a, b, c, d}, new float[] {s / (float) segments, s / (float) segments, (s + 1) / (float) segments,
						(s + 1) / (float) segments}, new float[] {1.0F - (r + 1) / (float) rings, 1.0F - r / (float) rings, 1.0F - r / (float) rings,
						1.0F - (r + 1) / (float) rings}, n);
			}
		}

		return this;
	}

	private static Vector3f point(Vector3f centre, float radius, float lat, float lon) {
		return new Vector3f(centre).add(Mth.cos(lat) * Mth.cos(lon) * radius, Mth.sin(lat) * radius, Mth.cos(lat) * Mth.sin(lon) * radius);
	}

	/** A flat ring (or disc with {@code inner} 0) facing {@code normal}-wise up (+y) or down. */
	public MeshBuilder ring(float cx, float y, float cz, float inner, float outer, int segments, boolean up) {
		for (int i = 0; i < segments; i++) {
			float a0 = Mth.TWO_PI * i / segments;
			float a1 = Mth.TWO_PI * (i + 1) / segments;
			Vector3f o0 = new Vector3f(cx + Mth.cos(a0) * outer, y, cz + Mth.sin(a0) * outer);
			Vector3f o1 = new Vector3f(cx + Mth.cos(a1) * outer, y, cz + Mth.sin(a1) * outer);
			Vector3f i0 = new Vector3f(cx + Mth.cos(a0) * inner, y, cz + Mth.sin(a0) * inner);
			Vector3f i1 = new Vector3f(cx + Mth.cos(a1) * inner, y, cz + Mth.sin(a1) * inner);

			this.polyFacing(inner > 0.0F ? List.of(i0, i1, o1, o0) : List.of(i0, o1, o0), new Vector3f(0.0F, up ? 1.0F : -1.0F, 0.0F));
		}

		return this;
	}

	/**
	 * A curved wall around (cx, cz) from angle a0 to a1 (degrees, 0 = east, 90 = south), from y0 to
	 * y1 at {@code radius}, facing inwards (or outwards).
	 */
	public MeshBuilder arcWall(float cx, float cz, float radius, float a0, float a1, float y0, float y1, int segments, boolean inward) {
		for (int i = 0; i < segments; i++) {
			float t0 = Mth.lerp(i / (float) segments, a0, a1) * Mth.DEG_TO_RAD;
			float t1 = Mth.lerp((i + 1) / (float) segments, a0, a1) * Mth.DEG_TO_RAD;
			Vector3f p0 = new Vector3f(cx + Mth.cos(t0) * radius, 0, cz + Mth.sin(t0) * radius);
			Vector3f p1 = new Vector3f(cx + Mth.cos(t1) * radius, 0, cz + Mth.sin(t1) * radius);

			Vector3f mid = new Vector3f(p0).add(p1).mul(0.5F);
			Vector3f facing = new Vector3f(cx - mid.x, 0.0F, cz - mid.z).mul(inward ? 1.0F : -1.0F);
			this.polyFacing(List.of(new Vector3f(p0.x, y1, p0.z), new Vector3f(p0.x, y0, p0.z), new Vector3f(p1.x, y0, p1.z), new Vector3f(p1.x, y1, p1.z)), facing);
		}

		return this;
	}

	// --- Output --------------------------------------------------------------------------------

	/** A quad with UVs in tile space (0..1). */
	private void emitTile(Vector3f[] p, float[] u, float[] v, Vector3f n) {
		this.emit(p, u, v, n);
	}

	private final float[] xyzuv = new float[20];
	private final int[] colours = new int[4];
	private final int[] samples = new int[15];

	private void emit(Vector3f[] p, float[] u, float[] v, Vector3f localNormal) {
		// The winding must match the normal (front faces are counter-clockwise): turn it if not.
		Vector3f geometric = new Vector3f();

		for (int i = 0; i < 4; i++) {
			Vector3f a = p[i];
			Vector3f b = p[(i + 1) % 4];
			geometric.x += (a.y - b.y) * (a.z + b.z);
			geometric.y += (a.z - b.z) * (a.x + b.x);
			geometric.z += (a.x - b.x) * (a.y + b.y);
		}

		if (geometric.dot(localNormal) < 0.0F) {
			p = new Vector3f[] {p[3], p[2], p[1], p[0]};
			u = new float[] {u[3], u[2], u[1], u[0]};
			v = new float[] {v[3], v[2], v[1], v[0]};
		}

		this.emitOne(p, u, v, localNormal, false);

		if (this.twoSided) {
			this.emitOne(new Vector3f[] {p[3], p[2], p[1], p[0]}, new float[] {u[3], u[2], u[1], u[0]}, new float[] {v[3], v[2], v[1], v[0]},
					new Vector3f(localNormal).negate(), true);
		}
	}

	private void emitOne(Vector3f[] p, float[] u, float[] v, Vector3f localNormal, boolean back) {
		this.matrix.normal(this.normalMatrix);
		Vector3f n = this.normalMatrix.transform(new Vector3f(localNormal)).normalize();
		int col = this.tile % InteriorAtlas.GRID;
		int row = this.tile / InteriorAtlas.GRID;
		float cell = 1.0F / InteriorAtlas.GRID;
		Vector3f centre = new Vector3f();
		int colour = this.colour;

		if (this.sun != null) {
			float light = this.sunAmbient + (1.0F - this.sunAmbient) * Math.max(0.0F, n.dot(this.sun));
			colour = Mesh.scale(colour, light, false);
		}

		for (int i = 0; i < 4; i++) {
			Vector3f w = this.matrix.transformPosition(new Vector3f(p[i]));
			centre.add(w);
			this.xyzuv[i * 5] = w.x;
			this.xyzuv[i * 5 + 1] = w.y;
			this.xyzuv[i * 5 + 2] = w.z;
			float uu = Mth.clamp(u[i], 0.0F, 1.0F);
			float vv = Mth.clamp(v[i], 0.0F, 1.0F);
			this.xyzuv[i * 5 + 3] = (col + INSET + uu * (1.0F - 2.0F * INSET)) * cell;
			this.xyzuv[i * 5 + 4] = (row + INSET + vv * (1.0F - 2.0F * INSET)) * cell;
			Vector3f lightA = this.lightA;
			Vector3f lightB = this.lightB;
			this.colours[i] = lightA == null || lightB == null ? colour : Mesh.scale(colour, falloff(p[i], lightA, lightB, this.lightInner, this.lightRadius), false);
			this.samples[i * 3] = Mth.floor(w.x + n.x * 0.45F);
			this.samples[i * 3 + 1] = Mth.floor(w.y + n.y * 0.45F);
			this.samples[i * 3 + 2] = Mth.floor(w.z + n.z * 0.45F);
		}

		centre.mul(0.25F);
		this.samples[12] = Mth.floor(centre.x + n.x * 0.55F);
		this.samples[13] = Mth.floor(centre.y + n.y * 0.55F);
		this.samples[14] = Mth.floor(centre.z + n.z * 0.55F);
		this.sink.quad(this.layer, this.xyzuv, n.x, n.y, n.z, this.colours, this.lightMode, this.fixedLight, this.inside ? this.anim | Mesh.INSIDE : this.anim, this.samples);
		this.quads++;
	}
}
