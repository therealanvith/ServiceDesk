/** The Google profile picture when there is one, otherwise the first letter of the name. */
export function Avatar({
  name,
  src,
  size = 28,
}: {
  name: string;
  src?: string | null;
  size?: number;
}) {
  const letter = name.trim().charAt(0).toUpperCase() || "?";
  return src ? (
    <img
      className="avatar"
      src={src}
      alt=""
      width={size}
      height={size}
      referrerPolicy="no-referrer"
    />
  ) : (
    <span
      className="avatar avatar-letter"
      style={{ width: size, height: size, fontSize: Math.round(size * 0.46) }}
      aria-hidden="true"
    >
      {letter}
    </span>
  );
}
